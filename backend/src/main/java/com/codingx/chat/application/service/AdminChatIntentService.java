package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供意图树后台管理服务，集中处理 ragent 字段与运行时旧字段的兼容派生。
 */
@Service
@RequiredArgsConstructor
public class AdminChatIntentService {

    private static final int KIND_KB = 0;
    private static final int KIND_SYSTEM = 1;
    private static final int KIND_MCP = 2;
    private static final String TYPE_KB = "kb";
    private static final String TYPE_SYSTEM = "system";
    private static final String TYPE_MCP = "mcp";

    private final ChatIntentNodeRepository chatIntentNodeRepository;

    /**
     * 返回后台管理平铺列表，输出前补齐 kind、sortOrder 等兼容字段。
     * @return 未删除节点列表。
     */
    public List<ChatIntentNode> listAllNodes() {
        return chatIntentNodeRepository.findAllNodes().stream()
            .map(this::normalizeForOutput)
            .toList();
    }

    /**
     * 返回树形节点，父节点缺失的历史脏数据会作为根节点输出，避免管理端节点不可见。
     * @return 树形意图节点列表。
     */
    public List<ChatIntentNode> listTree() {
        List<ChatIntentNode> nodes = chatIntentNodeRepository.findAllNodes().stream()
            .map(this::normalizeForOutput)
            .sorted(nodeComparator())
            .toList();
        Map<String, ChatIntentNode> nodeByCode = nodes.stream()
            .filter(node -> StrUtil.isNotBlank(node.getIntentCode()))
            .collect(Collectors.toMap(ChatIntentNode::getIntentCode, Function.identity(), (first, ignored) -> first));
        Map<String, List<ChatIntentNode>> childrenByParent = nodes.stream()
            .filter(node -> StrUtil.isNotBlank(node.getParentCode()))
            .collect(Collectors.groupingBy(ChatIntentNode::getParentCode));

        return nodes.stream()
            .filter(node -> StrUtil.isBlank(node.getParentCode()) || !nodeByCode.containsKey(node.getParentCode()))
            .map(node -> attachChildren(node, childrenByParent, new HashSet<>()))
            .toList();
    }

    /**
     * 创建或兼容保存节点；保留 POST 旧入口，同时补齐运行时仍依赖的 intentType 与 sortNo。
     * @param node 请求节点。
     * @return 已标准化并持久化的节点。
     */
    public ChatIntentNode save(ChatIntentNode node) {
        ChatIntentNode request = requireNode(node);
        validateRequired(request);
        Long excludedId = request.getId();
        rejectDuplicateIntentCode(request.getIntentCode(), excludedId);

        LocalDateTime now = LocalDateTime.now();
        ChatIntentNode existing = excludedId == null ? null : chatIntentNodeRepository.findById(excludedId);
        ChatIntentNode persisted = normalizeForSave(request.toBuilder()
            .id(excludedId == null ? IdUtil.getSnowflakeNextId() : excludedId)
            .createdAt(request.getCreatedAt() == null ? resolveCreatedAt(existing, now) : request.getCreatedAt())
            .updatedAt(now)
            .build());
        chatIntentNodeRepository.save(persisted);
        return normalizeForOutput(persisted);
    }

    /**
     * 按路径 ID 更新节点，路径 ID 优先于请求体 ID，防止错误覆盖其他配置。
     * @param id 路径主键。
     * @param node 请求节点。
     * @return 更新后的节点。
     */
    public ChatIntentNode update(Long id, ChatIntentNode node) {
        ChatIntentNode existing = chatIntentNodeRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("意图节点不存在");
        }
        ChatIntentNode request = requireNode(node).toBuilder()
            .id(id)
            .intentCode(StrUtil.blankToDefault(node.getIntentCode(), existing.getIntentCode()))
            .name(StrUtil.blankToDefault(node.getName(), existing.getName()))
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted() == null ? 0 : existing.getDeleted())
            .build();
        validateRequired(request);
        rejectDuplicateIntentCode(request.getIntentCode(), id);

        ChatIntentNode persisted = normalizeForSave(request);
        chatIntentNodeRepository.save(persisted);
        return normalizeForOutput(persisted);
    }

    /**
     * 逻辑删除节点；有子节点时禁止删除，避免后台树出现孤儿节点。
     * @param id 节点主键。
     */
    public void delete(Long id) {
        ChatIntentNode existing = chatIntentNodeRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("意图节点不存在");
        }
        if (chatIntentNodeRepository.hasChildren(existing.getIntentCode())) {
            throw new BusinessException("CHAT_INTENT_HAS_CHILDREN", "该意图存在子节点，不能删除");
        }
        chatIntentNodeRepository.softDeleteById(id);
    }

    private ChatIntentNode attachChildren(ChatIntentNode node, Map<String, List<ChatIntentNode>> childrenByParent, Set<String> visitedCodes) {
        if (StrUtil.isBlank(node.getIntentCode()) || visitedCodes.contains(node.getIntentCode())) {
            return node.toBuilder().children(List.of()).build();
        }
        Set<String> nextVisitedCodes = new HashSet<>(visitedCodes);
        nextVisitedCodes.add(node.getIntentCode());
        List<ChatIntentNode> children = childrenByParent.getOrDefault(node.getIntentCode(), List.of()).stream()
            .sorted(nodeComparator())
            .map(child -> attachChildren(child, childrenByParent, nextVisitedCodes))
            .toList();
        return node.toBuilder()
            .level(node.getLevel() == null ? nextVisitedCodes.size() : node.getLevel())
            .children(children)
            .build();
    }

    private ChatIntentNode normalizeForSave(ChatIntentNode node) {
        KindType kindType = resolveKindType(node.getKind(), node.getIntentType());
        int sortValue = resolveSortValue(node);
        return node.toBuilder()
            .intentType(kindType.intentType())
            .kind(kindType.kind())
            .level(node.getLevel() == null ? 1 : node.getLevel())
            .enabled(node.getEnabled() == null ? 1 : node.getEnabled())
            .sortNo(sortValue)
            .sortOrder(sortValue)
            .deleted(node.getDeleted() == null ? 0 : node.getDeleted())
            .children(List.of())
            .build();
    }

    private ChatIntentNode normalizeForOutput(ChatIntentNode node) {
        KindType kindType = resolveKindType(node.getKind(), node.getIntentType());
        int sortValue = resolveSortValue(node);
        return node.toBuilder()
            .intentType(kindType.intentType())
            .kind(kindType.kind())
            .sortNo(sortValue)
            .sortOrder(sortValue)
            .children(node.getChildren() == null ? List.of() : node.getChildren())
            .build();
    }

    private void validateRequired(ChatIntentNode node) {
        if (StrUtil.isBlank(node.getIntentCode())) {
            throw new BusinessException("CHAT_INTENT_INVALID", "意图编码不能为空");
        }
        if (StrUtil.isBlank(node.getName())) {
            throw new BusinessException("CHAT_INTENT_INVALID", "意图名称不能为空");
        }
    }

    private void rejectDuplicateIntentCode(String intentCode, Long excludedId) {
        if (chatIntentNodeRepository.existsByIntentCode(intentCode, excludedId)) {
            throw new BusinessException("CHAT_INTENT_DUPLICATE_CODE", "意图编码已存在");
        }
    }

    private KindType resolveKindType(Integer kind, String intentType) {
        if (kind != null) {
            return new KindType(kind, intentTypeFromKind(kind));
        }
        if (StrUtil.isNotBlank(intentType)) {
            String normalizedIntentType = intentType.trim().toLowerCase();
            return new KindType(kindFromIntentType(normalizedIntentType), normalizedIntentType);
        }
        return new KindType(KIND_KB, TYPE_KB);
    }

    private String intentTypeFromKind(Integer kind) {
        return switch (kind) {
            case KIND_KB -> TYPE_KB;
            case KIND_SYSTEM -> TYPE_SYSTEM;
            case KIND_MCP -> TYPE_MCP;
            default -> throw new BusinessException("CHAT_INTENT_INVALID_KIND", "意图类型不支持");
        };
    }

    private Integer kindFromIntentType(String intentType) {
        return switch (intentType) {
            case TYPE_KB -> KIND_KB;
            case TYPE_SYSTEM -> KIND_SYSTEM;
            case TYPE_MCP -> KIND_MCP;
            default -> throw new BusinessException("CHAT_INTENT_INVALID_KIND", "意图类型不支持");
        };
    }

    private int resolveSortValue(ChatIntentNode node) {
        if (node.getSortOrder() != null) {
            return node.getSortOrder();
        }
        if (node.getSortNo() != null) {
            return node.getSortNo();
        }
        return 0;
    }

    private Comparator<ChatIntentNode> nodeComparator() {
        return Comparator.comparingInt(this::resolveSortValue)
            .thenComparing(node -> StrUtil.blankToDefault(node.getIntentCode(), ""));
    }

    private LocalDateTime resolveCreatedAt(ChatIntentNode existing, LocalDateTime now) {
        return existing == null || existing.getCreatedAt() == null ? now : existing.getCreatedAt();
    }

    private ChatIntentNode requireNode(ChatIntentNode node) {
        if (node == null) {
            throw new BusinessException("CHAT_INTENT_INVALID", "意图节点不能为空");
        }
        return node;
    }

    /**
     * 管理端 kind 与运行时 intentType 的成对结果，避免派生逻辑在保存和查询中分叉。
     * @param kind 管理端节点类型。
     * @param intentType 运行时意图类型。
     */
    private record KindType(Integer kind, String intentType) {
    }
}
