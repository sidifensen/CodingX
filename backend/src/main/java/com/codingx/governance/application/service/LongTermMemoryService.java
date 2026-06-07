package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.domain.repository.GovernanceLongTermMemoryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 长期记忆应用服务，负责从已完成对话中提取可直接回注的长期记忆，并为模型上下文检索 ACTIVE 记忆。
 */
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private static final int DEFAULT_USER_LIST_LIMIT = 100;
    private static final int MAX_CONTEXT_MEMORY_COUNT = 6;

    /** 长期记忆仓储，用于记忆去重、状态更新和上下文检索。 */
    private final GovernanceLongTermMemoryRepository memoryRepository;

    /**
     * 从用户与助手的一轮完成对话中提取长期记忆。
     * @param conversation 当前会话，提供用户与工作空间归属。
     * @param userMessage 用户消息，显式记忆信号从这里识别。
     * @param assistantMessage 已完成助手消息，作为来源链路的完成证据。
     * @return 本次新保存且立即生效的 ACTIVE 记忆。
     */
    public List<GovernanceLongTermMemory> extractCandidates(
        ChatConversation conversation,
        ChatMessage userMessage,
        ChatMessage assistantMessage
    ) {
        // 步骤 1：只有完整会话和用户输入都存在时才尝试提取，避免异常分支污染长期记忆。
        if (conversation == null || userMessage == null || StrUtil.isBlank(userMessage.getContent()) || assistantMessage == null) {
            return List.of();
        }
        String userContent = StrUtil.trimToEmpty(userMessage.getContent());
        if (!containsMemorySignal(userContent)) {
            return List.of();
        }
        // 步骤 2：显式信号后的正文作为记忆内容，状态直接置为 ACTIVE，下一轮相关问题即可被上下文检索回注。
        String memoryContent = normalizeMemoryContent(userContent);
        if (StrUtil.isBlank(memoryContent)) {
            return List.of();
        }
        String scope = resolveScope(userContent);
        // 用户级记忆是跨项目偏好，不能绑定具体工作空间；项目级记忆才用于当前仓库约束。
        Long memoryWorkspaceId = "PROJECT".equals(scope) ? conversation.getWorkspaceId() : null;
        String keywordJson = JSONUtil.toJsonStr(extractKeywords(memoryContent));
        String memoryKey = buildMemoryKey(scope, conversation.getCreatedBy(), memoryWorkspaceId, memoryContent);
        if (memoryRepository.findByMemoryKey(memoryKey) != null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        GovernanceLongTermMemory memory = GovernanceLongTermMemory.builder()
            .id(IdUtil.getSnowflakeNextId())
            .memoryScope(scope)
            .userId(conversation.getCreatedBy())
            .workspaceId(memoryWorkspaceId)
            .memoryKey(memoryKey)
            .content(memoryContent)
            .status("ACTIVE")
            .sourceType("CHAT_EXCHANGE")
            .sourceConversationId(conversation.getId())
            .sourceMessageId(userMessage.getId())
            .keywordJson(keywordJson)
            .confidenceScore(BigDecimal.valueOf(0.80D))
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        memoryRepository.save(memory);
        return List.of(memory);
    }

    /**
     * 查询用户可见的记忆列表，供用户端查看当前会进入模型上下文的记忆。
     * @param userId 当前用户 ID。
     * @param workspaceId 当前工作空间 ID，可为空。
     * @param status 状态筛选，可为空。
     * @return 用户可见记忆列表。
     */
    public List<GovernanceLongTermMemory> listUserMemories(Long userId, Long workspaceId, String status) {
        requireUserId(userId);
        return memoryRepository.findForUser(userId, workspaceId, normalizeStatusOrNull(status), DEFAULT_USER_LIST_LIMIT);
    }

    /**
     * 查询管理端长期记忆列表。
     * @param status 状态筛选，可为空。
     * @param limit 最大返回条数。
     * @return 长期记忆列表。
     */
    public List<GovernanceLongTermMemory> listAdminMemories(String status, int limit) {
        return memoryRepository.findForAdmin(normalizeStatusOrNull(status), limit);
    }

    /**
     * 用户管理自己的长期记忆状态；当前提取后的记忆默认 ACTIVE，此入口仅用于启用或停用已有记忆。
     * @param memoryId 记忆主键。
     * @param userId 当前用户 ID。
     * @param status 目标状态，仅允许 ACTIVE 或 REJECTED。
     * @return 更新后的记忆。
     */
    public GovernanceLongTermMemory updateUserMemoryStatus(Long memoryId, Long userId, String status) {
        requireUserId(userId);
        GovernanceLongTermMemory memory = requireMemory(memoryId);
        requireOwnedMemory(memory, userId);
        return updateStatus(memory, status);
    }

    /**
     * 用户编辑自己的长期记忆正文；编辑后的内容会刷新关键词和去重键，确保后续回注使用最新约定。
     * @param memoryId 记忆主键。
     * @param userId 当前用户 ID。
     * @param content 新记忆正文。
     * @return 更新后的记忆。
     */
    public GovernanceLongTermMemory updateUserMemoryContent(Long memoryId, Long userId, String content) {
        requireUserId(userId);
        GovernanceLongTermMemory memory = requireMemory(memoryId);
        requireOwnedMemory(memory, userId);
        String normalizedContent = normalizeEditableMemoryContent(content);
        if (StrUtil.isBlank(normalizedContent)) {
            throw new BusinessException("GOVERNANCE_MEMORY_CONTENT_REQUIRED", "长期记忆内容不能为空");
        }
        String memoryKey = buildMemoryKey(
            StrUtil.blankToDefault(memory.getMemoryScope(), "USER"),
            memory.getUserId(),
            memory.getWorkspaceId(),
            normalizedContent
        );
        GovernanceLongTermMemory updated = memory.toBuilder()
            .content(normalizedContent)
            .memoryKey(memoryKey)
            .keywordJson(JSONUtil.toJsonStr(extractKeywords(normalizedContent)))
            .updatedAt(LocalDateTime.now())
            .deleted(memory.getDeleted() == null ? 0 : memory.getDeleted())
            .build();
        memoryRepository.save(updated);
        return updated;
    }

    /**
     * 用户删除自己的长期记忆；使用逻辑删除以保留来源审计链路，删除后不再参与列表和上下文回注。
     * @param memoryId 记忆主键。
     * @param userId 当前用户 ID。
     */
    public void deleteUserMemory(Long memoryId, Long userId) {
        requireUserId(userId);
        GovernanceLongTermMemory memory = requireMemory(memoryId);
        requireOwnedMemory(memory, userId);
        GovernanceLongTermMemory deleted = memory.toBuilder()
            .deleted(1)
            .updatedAt(LocalDateTime.now())
            .build();
        memoryRepository.save(deleted);
    }

    /**
     * 管理端更新任意长期记忆状态，用于启用或停用已提取的记忆。
     * @param memoryId 记忆主键。
     * @param status 目标状态。
     * @return 更新后的记忆。
     */
    public GovernanceLongTermMemory updateAdminMemoryStatus(Long memoryId, String status) {
        return updateStatus(requireMemory(memoryId), status);
    }

    /**
     * 统计当前用户在工作空间下已生效记忆数量。
     * @param userId 用户 ID。
     * @param workspaceId 工作空间 ID，可为空。
     * @return 已生效数量。
     */
    public int countActiveByUserAndWorkspace(Long userId, Long workspaceId) {
        if (userId == null) {
            return 0;
        }
        return memoryRepository.countActiveByUserAndWorkspace(userId, workspaceId);
    }

    /**
     * 检索可参与模型上下文回注的 ACTIVE 记忆。
     * @param userId 当前用户 ID。
     * @param workspaceId 当前工作空间 ID，可为空。
     * @param query 用户本轮问题，用于关键词命中过滤。
     * @param limit 最大返回条数。
     * @return 命中的 ACTIVE 记忆。
     */
    public List<GovernanceLongTermMemory> retrieveActiveMemories(Long userId, Long workspaceId, String query, int limit) {
        if (userId == null) {
            return List.of();
        }
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_CONTEXT_MEMORY_COUNT);
        String normalizedQuery = StrUtil.trimToEmpty(query);
        return memoryRepository.findActiveForContext(userId, workspaceId, normalizedLimit * 3).stream()
            .filter(memory -> matchesQuery(memory, normalizedQuery))
            .limit(normalizedLimit)
            .toList();
    }

    private GovernanceLongTermMemory updateStatus(GovernanceLongTermMemory memory, String status) {
        String normalizedStatus = normalizeWritableStatus(status);
        GovernanceLongTermMemory updated = memory.toBuilder()
            .status(normalizedStatus)
            .updatedAt(LocalDateTime.now())
            .build();
        memoryRepository.save(updated);
        return updated;
    }

    private GovernanceLongTermMemory requireMemory(Long memoryId) {
        if (memoryId == null) {
            throw new BusinessException("GOVERNANCE_MEMORY_ID_REQUIRED", "长期记忆ID不能为空");
        }
        GovernanceLongTermMemory memory = memoryRepository.findById(memoryId);
        if (memory == null) {
            throw new BusinessException("GOVERNANCE_MEMORY_NOT_FOUND", "长期记忆不存在");
        }
        return memory;
    }

    private void requireOwnedMemory(GovernanceLongTermMemory memory, Long userId) {
        if (memory == null || !userId.equals(memory.getUserId())) {
            throw new BusinessException("GOVERNANCE_MEMORY_FORBIDDEN", "无权操作该长期记忆");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("GOVERNANCE_MEMORY_USER_REQUIRED", "用户身份不能为空");
        }
    }

    private String normalizeWritableStatus(String status) {
        String normalizedStatus = StrUtil.trimToEmpty(status).toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalizedStatus) && !"REJECTED".equals(normalizedStatus)) {
            throw new BusinessException("GOVERNANCE_MEMORY_STATUS_INVALID", "长期记忆状态不合法");
        }
        return normalizedStatus;
    }

    private String normalizeStatusOrNull(String status) {
        String normalizedStatus = StrUtil.trimToEmpty(status).toUpperCase(Locale.ROOT);
        return StrUtil.isBlank(normalizedStatus) || "ALL".equals(normalizedStatus) ? null : normalizedStatus;
    }

    private boolean containsMemorySignal(String content) {
        return StrUtil.containsAny(content, "记住", "请记忆", "长期保存", "以后都按", "我的偏好");
    }

    private String resolveScope(String content) {
        if (StrUtil.containsAny(content, "项目", "仓库", "代码库", "团队约定")) {
            return "PROJECT";
        }
        return "USER";
    }

    private String normalizeMemoryContent(String content) {
        String normalized = StrUtil.trimToEmpty(content)
            .replace("[[skill:", "")
            .replace("]]", "");
        for (String separator : List.of("：", ":", "，", ",")) {
            int index = normalized.indexOf(separator);
            if (index >= 0 && index + 1 < normalized.length()) {
                normalized = normalized.substring(index + 1);
                break;
            }
        }
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return StrUtil.maxLength(normalized, 300);
    }

    private String normalizeEditableMemoryContent(String content) {
        return StrUtil.maxLength(StrUtil.trimToEmpty(content).replaceAll("\\s+", " "), 300);
    }

    private List<String> extractKeywords(String content) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = content.replaceAll("[，。；;:：、\\s]+", " ").trim();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2) {
                keywords.add(StrUtil.maxLength(token, 16));
            }
        }
        if (content.contains("业务注释")) {
            keywords.add("业务注释");
        }
        if (content.contains("代码风格")) {
            keywords.add("代码风格");
        }
        if (keywords.isEmpty()) {
            keywords.add(StrUtil.maxLength(content, 16));
        }
        return new ArrayList<>(keywords).stream().limit(8).toList();
    }

    private boolean matchesQuery(GovernanceLongTermMemory memory, String query) {
        if (memory == null || !"ACTIVE".equals(memory.getStatus())) {
            return false;
        }
        if (StrUtil.isBlank(query)) {
            return true;
        }
        String content = StrUtil.blankToDefault(memory.getContent(), "");
        if (query.contains(content) || content.contains(query)) {
            return true;
        }
        for (String keyword : parseKeywords(memory.getKeywordJson())) {
            if (StrUtil.isNotBlank(keyword) && query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseKeywords(String keywordJson) {
        if (StrUtil.isBlank(keywordJson) || !JSONUtil.isTypeJSONArray(keywordJson)) {
            return List.of();
        }
        JSONArray array = JSONUtil.parseArray(keywordJson);
        return array.stream().map(String::valueOf).filter(StrUtil::isNotBlank).toList();
    }

    private String buildMemoryKey(String scope, Long userId, Long workspaceId, String content) {
        String rawKey = StrUtil.join(":", scope, userId, workspaceId, StrUtil.trimToEmpty(content).toLowerCase(Locale.ROOT));
        return scope.toLowerCase(Locale.ROOT) + ":" + DigestUtil.sha256Hex(rawKey.getBytes(StandardCharsets.UTF_8));
    }
}
