package com.codingx.admin.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.common.error.ErrorMessageCatalog;
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
 * 提供意图树后台管理服务，集中处理配置台字段与运行时旧字段的兼容派生。
 */
@Service
@RequiredArgsConstructor
public class AdminChatIntentService {

    /**
     * 管理端“搜索意图”类型值，对应运行时 search。
     */
    private static final int KIND_SEARCH = 0;

    /**
     * 管理端“系统意图”类型值，对应运行时 system。
     */
    private static final int KIND_SYSTEM = 1;

    /**
     * 管理端“MCP 工具意图”类型值，对应运行时 mcp。
     */
    private static final int KIND_MCP = 2;

    /**
     * 运行时搜索意图类型，聊天路由按该值进入检索链路。
     */
    private static final String TYPE_SEARCH = "search";

    /**
     * 运行时系统意图类型，聊天路由按该值进入系统能力链路。
     */
    private static final String TYPE_SYSTEM = "system";

    /**
     * 运行时 MCP 意图类型，聊天路由按该值进入工具执行链路。
     */
    private static final String TYPE_MCP = "mcp";

    /**
     * 意图节点仓储，负责管理端配置和运行时意图树的持久化访问。
     */
    private final ChatIntentNodeRepository chatIntentNodeRepository;

    /**
     * 返回后台管理平铺列表，输出前补齐 kind、sortOrder 等兼容字段。
     * @return 未删除节点列表。
     */
    public List<ChatIntentNode> listAllNodes() {
        // 步骤 1：读取未删除节点并统一补齐 kind/intentType/sort 字段，避免前端兼容旧数据。
        return chatIntentNodeRepository.findAllNodes().stream()
            .map(this::normalizeForOutput)
            .toList();
    }

    /**
     * 返回树形节点，父节点缺失的历史脏数据会作为根节点输出，避免管理端节点不可见。
     * @return 树形意图节点列表。
     */
    public List<ChatIntentNode> listTree() {
        // 步骤 1：读取平铺节点后先做输出标准化和稳定排序，保证树形结果顺序可预测。
        List<ChatIntentNode> nodes = chatIntentNodeRepository.findAllNodes().stream()
            .map(this::normalizeForOutput)
            .sorted(nodeComparator())
            .toList();
        // 步骤 2：按 intentCode 建立索引，用于判断父节点是否存在。
        Map<String, ChatIntentNode> nodeByCode = nodes.stream()
            .filter(node -> StrUtil.isNotBlank(node.getIntentCode()))
            .collect(Collectors.toMap(ChatIntentNode::getIntentCode, Function.identity(), (first, ignored) -> first));
        // 步骤 3：按 parentCode 分组子节点，后续递归挂载时避免重复扫描全量列表。
        Map<String, List<ChatIntentNode>> childrenByParent = nodes.stream()
            .filter(node -> StrUtil.isNotBlank(node.getParentCode()))
            .collect(Collectors.groupingBy(ChatIntentNode::getParentCode));

        // 步骤 4：根节点包含无父节点和父节点缺失的历史脏数据，避免管理端看不到孤儿配置。
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
        // 步骤 1：兼容旧 POST 入口，先确保请求体存在并具备最小必填字段。
        ChatIntentNode request = requireNode(node);
        validateRequired(request);
        // 步骤 2：新增或保存时都要检查意图编码唯一性，排除自身 ID 以兼容旧保存入口。
        Long excludedId = request.getId();
        rejectDuplicateIntentCode(request.getIntentCode(), excludedId);

        LocalDateTime now = LocalDateTime.now();
        // 步骤 3：如果请求带 ID，则读取旧记录保留创建时间；否则生成新主键和审计时间。
        ChatIntentNode existing = excludedId == null ? null : chatIntentNodeRepository.findById(excludedId);
        ChatIntentNode persisted = normalizeForSave(request.toBuilder()
            .id(excludedId == null ? IdUtil.getSnowflakeNextId() : excludedId)
            .createdAt(request.getCreatedAt() == null ? resolveCreatedAt(existing, now) : request.getCreatedAt())
            .updatedAt(now)
            .build());
        // 步骤 4：保存前已完成兼容字段派生，保存后返回再次标准化的运行时视图。
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
        // 步骤 1：路径 ID 是更新目标的唯一来源，查不到时返回不存在错误。
        ChatIntentNode existing = chatIntentNodeRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_INTENT_NOT_FOUND);
        }
        // 步骤 2：请求体缺省字段回退到已有记录，避免 PATCH 风格调用误清空关键配置。
        ChatIntentNode incoming = requireNode(node);
        ChatIntentNode request = incoming.toBuilder()
            .id(id)
            .intentCode(StrUtil.blankToDefault(incoming.getIntentCode(), existing.getIntentCode()))
            .parentCode(normalizeParentCode(incoming.getParentCode()))
            .name(StrUtil.blankToDefault(incoming.getName(), existing.getName()))
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted() == null ? 0 : existing.getDeleted())
            .build();
        // 步骤 3：合并后的节点再校验必填和编码唯一性，保证最终入库状态有效。
        validateRequired(request);
        rejectDuplicateIntentCode(request.getIntentCode(), id);

        // 步骤 4：保存前派生运行时兼容字段，返回时补齐管理端展示字段。
        ChatIntentNode persisted = normalizeForSave(request);
        chatIntentNodeRepository.save(persisted);
        return normalizeForOutput(persisted);
    }

    /**
     * 逻辑删除节点；有子节点时禁止删除，避免后台树出现孤儿节点。
     * @param id 节点主键。
     */
    public void delete(Long id) {
        // 步骤 1：删除前确认节点存在，避免重复删除造成管理端误判。
        ChatIntentNode existing = chatIntentNodeRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_INTENT_NOT_FOUND);
        }
        // 步骤 2：仍有子节点时阻止删除，防止树结构出现不可维护的孤儿节点。
        if (chatIntentNodeRepository.hasChildren(existing.getIntentCode())) {
            throw new BusinessException("CHAT_INTENT_HAS_CHILDREN", ErrorMessageCatalog.CHAT_INTENT_HAS_CHILDREN);
        }
        // 步骤 3：执行软删除，保留历史配置用于审计和恢复。
        chatIntentNodeRepository.softDeleteById(id);
    }

    private ChatIntentNode attachChildren(ChatIntentNode node, Map<String, List<ChatIntentNode>> childrenByParent, Set<String> visitedCodes) {
        // 步骤 1：无编码或递归链路中已访问过的节点直接截断，避免脏数据导致无限递归。
        if (StrUtil.isBlank(node.getIntentCode()) || visitedCodes.contains(node.getIntentCode())) {
            return node.toBuilder().children(List.of()).build();
        }
        // 步骤 2：复制访问集合后追加当前节点，确保兄弟分支互不污染。
        Set<String> nextVisitedCodes = new HashSet<>(visitedCodes);
        nextVisitedCodes.add(node.getIntentCode());
        // 步骤 3：按排序规则递归挂载子节点，输出给管理端树组件直接消费。
        List<ChatIntentNode> children = childrenByParent.getOrDefault(node.getIntentCode(), List.of()).stream()
            .sorted(nodeComparator())
            .map(child -> attachChildren(child, childrenByParent, nextVisitedCodes))
            .toList();
        // 步骤 4：缺失层级时用访问深度兜底，避免旧数据在前端展示层级为空。
        return node.toBuilder()
            .level(node.getLevel() == null ? nextVisitedCodes.size() : node.getLevel())
            .children(children)
            .build();
    }

    private ChatIntentNode normalizeForSave(ChatIntentNode node) {
        // 步骤 1：统一解析管理端 kind 与运行时 intentType，保证两套字段同步。
        KindType kindType = resolveKindType(node.getKind(), node.getIntentType());
        // 步骤 2：sortOrder 和 sortNo 是新旧排序字段，保存时写入同一个排序值。
        int sortValue = resolveSortValue(node);
        // 步骤 3：父节点编码统一修剪空白，空字符串按根节点处理。
        String parentCode = normalizeParentCode(node.getParentCode());
        return node.toBuilder()
            .parentCode(parentCode)
            .intentType(kindType.intentType())
            .kind(kindType.kind())
            .level(resolveLevel(node.getLevel(), parentCode))
            .enabled(node.getEnabled() == null ? 1 : node.getEnabled())
            .sortNo(sortValue)
            .sortOrder(sortValue)
            .deleted(node.getDeleted() == null ? 0 : node.getDeleted())
            .children(List.of())
            .build();
    }

    private ChatIntentNode normalizeForOutput(ChatIntentNode node) {
        // 步骤 1：输出时也做 kind/intentType 双向派生，兼容旧库里缺任一字段的记录。
        KindType kindType = resolveKindType(node.getKind(), node.getIntentType());
        // 步骤 2：统一排序值并补齐空 children，前端无需判断 null。
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
        // 步骤 1：意图编码是路由和树关系的稳定标识，不能为空。
        if (StrUtil.isBlank(node.getIntentCode())) {
            throw new BusinessException("CHAT_INTENT_INVALID", ErrorMessageCatalog.CHAT_INTENT_CODE_REQUIRED);
        }
        // 步骤 2：节点名称是管理端展示和维护入口，不能为空。
        if (StrUtil.isBlank(node.getName())) {
            throw new BusinessException("CHAT_INTENT_INVALID", ErrorMessageCatalog.CHAT_INTENT_NAME_REQUIRED);
        }
    }

    private void rejectDuplicateIntentCode(String intentCode, Long excludedId) {
        // 步骤 1：同一意图编码只能对应一个有效节点，更新时排除当前节点自身。
        if (chatIntentNodeRepository.existsByIntentCode(intentCode, excludedId)) {
            throw new BusinessException("CHAT_INTENT_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_INTENT_DUPLICATE_CODE);
        }
    }

    private KindType resolveKindType(Integer kind, String intentType) {
        // 步骤 1：管理端 kind 优先，保证后台显式选择不会被旧 intentType 覆盖。
        if (kind != null) {
            return new KindType(kind, intentTypeFromKind(kind));
        }
        // 步骤 2：旧数据只有 intentType 时反推 kind，保证管理端表单仍能展示类型。
        if (StrUtil.isNotBlank(intentType)) {
            String normalizedIntentType = intentType.trim().toLowerCase();
            return new KindType(kindFromIntentType(normalizedIntentType), normalizedIntentType);
        }
        // 步骤 3：两者都缺失时按搜索意图兜底，保持历史默认行为。
        return new KindType(KIND_SEARCH, TYPE_SEARCH);
    }

    private String intentTypeFromKind(Integer kind) {
        // 步骤 1：只允许管理端定义过的类型值，未知值直接阻断保存。
        return switch (kind) {
            case KIND_SEARCH -> TYPE_SEARCH;
            case KIND_SYSTEM -> TYPE_SYSTEM;
            case KIND_MCP -> TYPE_MCP;
            default -> throw new BusinessException("CHAT_INTENT_INVALID_KIND", ErrorMessageCatalog.CHAT_INTENT_KIND_UNSUPPORTED);
        };
    }

    private Integer kindFromIntentType(String intentType) {
        // 步骤 1：运行时字符串类型回映射为管理端枚举，未知类型按配置错误处理。
        return switch (intentType) {
            case TYPE_SEARCH -> KIND_SEARCH;
            case TYPE_SYSTEM -> KIND_SYSTEM;
            case TYPE_MCP -> KIND_MCP;
            default -> throw new BusinessException("CHAT_INTENT_INVALID_KIND", ErrorMessageCatalog.CHAT_INTENT_KIND_UNSUPPORTED);
        };
    }

    private int resolveSortValue(ChatIntentNode node) {
        // 步骤 1：新字段 sortOrder 优先，避免管理端调整排序后被旧字段覆盖。
        if (node.getSortOrder() != null) {
            return node.getSortOrder();
        }
        // 步骤 2：旧字段 sortNo 作为兼容兜底，保证历史数据排序不丢失。
        if (node.getSortNo() != null) {
            return node.getSortNo();
        }
        // 步骤 3：未配置排序时按 0 处理，由后续 intentCode 排序保证稳定输出。
        return 0;
    }

    private int resolveLevel(Integer level, String parentCode) {
        // 步骤 1：显式层级优先保留，避免保存时改变人工维护的层级。
        if (level != null) {
            return level;
        }
        // 步骤 2：缺失层级按是否有父节点兜底，根节点为 0，子节点至少为 1。
        return StrUtil.isBlank(parentCode) ? 0 : 1;
    }

    private String normalizeParentCode(String parentCode) {
        // 步骤 1：父节点编码只做空白归一化，不校验存在性，树查询阶段负责孤儿兜底展示。
        return StrUtil.emptyToNull(StrUtil.trim(parentCode));
    }

    private Comparator<ChatIntentNode> nodeComparator() {
        // 步骤 1：先按排序值，再按意图编码排序，保证相同权重下输出稳定。
        return Comparator.comparingInt(this::resolveSortValue)
            .thenComparing(node -> StrUtil.blankToDefault(node.getIntentCode(), ""));
    }

    private LocalDateTime resolveCreatedAt(ChatIntentNode existing, LocalDateTime now) {
        // 步骤 1：旧记录已有创建时间时保留，否则使用当前时间补齐审计字段。
        return existing == null || existing.getCreatedAt() == null ? now : existing.getCreatedAt();
    }

    private ChatIntentNode requireNode(ChatIntentNode node) {
        // 步骤 1：请求体为空时没有可保存的业务字段，统一返回意图节点必填错误。
        if (node == null) {
            throw new BusinessException("CHAT_INTENT_INVALID", ErrorMessageCatalog.CHAT_INTENT_NODE_REQUIRED);
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
