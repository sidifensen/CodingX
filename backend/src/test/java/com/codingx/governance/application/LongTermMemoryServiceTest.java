package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.domain.repository.GovernanceLongTermMemoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证长期记忆候选提取、确认和检索的确定性规则。
 */
class LongTermMemoryServiceTest {

    /**
     * 显式记忆信号只应生成 PENDING 候选，不能直接影响后续上下文。
     */
    @Test
    void extractCandidatesShouldPersistPendingUserMemory() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        LongTermMemoryService service = new LongTermMemoryService(repository);
        ChatConversation conversation = ChatConversation.create(100L, "偏好", 200L, 300L, ChatConversationStatus.ACTIVE);
        ChatMessage userMessage = ChatMessage.userMessage(100L, "请记住我的代码风格偏好：优先写清楚业务注释");
        ChatMessage assistantMessage = ChatMessage.assistantMessage(100L, "已记录，后续会按这个偏好处理。", ChatMessageStatus.COMPLETED, null, null, null);

        List<GovernanceLongTermMemory> memories = service.extractCandidates(conversation, userMessage, assistantMessage);

        assertEquals(1, memories.size());
        GovernanceLongTermMemory memory = memories.getFirst();
        assertEquals("PENDING", memory.getStatus());
        assertEquals("USER", memory.getMemoryScope());
        assertEquals(200L, memory.getUserId());
        assertNull(memory.getWorkspaceId());
        assertEquals(100L, memory.getSourceConversationId());
        assertTrue(memory.getContent().contains("优先写清楚业务注释"));
        assertTrue(memory.getKeywordJson().contains("业务注释"));
    }

    /**
     * 项目级记忆必须绑定当前工作空间，避免其他项目误用团队约定。
     */
    @Test
    void extractCandidatesShouldPersistProjectMemoryWithWorkspaceScope() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        LongTermMemoryService service = new LongTermMemoryService(repository);
        ChatConversation conversation = ChatConversation.create(100L, "项目约定", 200L, 300L, ChatConversationStatus.ACTIVE);
        ChatMessage userMessage = ChatMessage.userMessage(100L, "以后都按项目注释规范：Java 代码必须说明业务意图");
        ChatMessage assistantMessage = ChatMessage.assistantMessage(100L, "已收到。", ChatMessageStatus.COMPLETED, null, null, null);

        List<GovernanceLongTermMemory> memories = service.extractCandidates(conversation, userMessage, assistantMessage);

        assertEquals(1, memories.size());
        GovernanceLongTermMemory memory = memories.getFirst();
        assertEquals("PROJECT", memory.getMemoryScope());
        assertEquals(300L, memory.getWorkspaceId());
    }

    /**
     * 相同 key 的候选已经存在时不能重复保存，避免记忆列表被同一偏好刷屏。
     */
    @Test
    void extractCandidatesShouldDeduplicateByMemoryKey() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        LongTermMemoryService service = new LongTermMemoryService(repository);
        ChatConversation conversation = ChatConversation.create(100L, "偏好", 200L, 300L, ChatConversationStatus.ACTIVE);
        ChatMessage userMessage = ChatMessage.userMessage(100L, "请记住我的代码风格偏好：优先写清楚业务注释");
        ChatMessage assistantMessage = ChatMessage.assistantMessage(100L, "已记录。", ChatMessageStatus.COMPLETED, null, null, null);

        service.extractCandidates(conversation, userMessage, assistantMessage);
        service.extractCandidates(conversation, userMessage, assistantMessage);

        assertEquals(1, repository.savedMemories.size());
    }

    /**
     * 用户确认或拒绝候选时应更新状态，只有 ACTIVE 记忆能参与检索回注。
     */
    @Test
    void updateUserMemoryStatusShouldActivateOrRejectPendingMemory() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        GovernanceLongTermMemory memory = sampleMemory("PENDING").toBuilder().id(500L).userId(200L).build();
        repository.save(memory);
        LongTermMemoryService service = new LongTermMemoryService(repository);

        GovernanceLongTermMemory activated = service.updateUserMemoryStatus(500L, 200L, "ACTIVE");
        GovernanceLongTermMemory rejected = service.updateUserMemoryStatus(500L, 200L, "REJECTED");

        assertEquals("ACTIVE", activated.getStatus());
        assertEquals("REJECTED", rejected.getStatus());
        assertTrue(rejected.getUpdatedAt() != null);
    }

    /**
     * 检索只返回当前用户/项目范围内的 ACTIVE 且关键词命中的记忆。
     */
    @Test
    void retrieveActiveMemoriesShouldFilterByScopeStatusAndKeywords() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(1L).userId(200L).workspaceId(null).content("代码风格偏好：业务注释").keywordJson("[\"业务注释\"]").build());
        repository.save(sampleMemory("PENDING").toBuilder().id(2L).userId(200L).workspaceId(300L).content("待确认").keywordJson("[\"业务注释\"]").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(3L).userId(201L).workspaceId(300L).content("其他用户").keywordJson("[\"业务注释\"]").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(4L).userId(200L).workspaceId(301L).memoryScope("PROJECT").content("其他项目").keywordJson("[\"业务注释\"]").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        List<GovernanceLongTermMemory> memories = service.retrieveActiveMemories(200L, 300L, "请按业务注释规范修改代码", 10);

        assertEquals(1, memories.size());
        assertEquals(1L, memories.getFirst().getId());
    }

    /**
     * 没有工作空间上下文时只能检索用户级记忆，避免把其他项目约定注入云端或未绑定会话。
     */
    @Test
    void retrieveActiveMemoriesWithoutWorkspaceShouldExcludeProjectMemories() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(1L).workspaceId(null).content("用户偏好：业务注释").keywordJson("[\"业务注释\"]").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(2L).memoryScope("PROJECT").workspaceId(300L).content("项目约定：业务注释").keywordJson("[\"业务注释\"]").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        List<GovernanceLongTermMemory> memories = service.retrieveActiveMemories(200L, null, "业务注释", 10);

        assertEquals(1, memories.size());
        assertEquals(1L, memories.getFirst().getId());
    }

    /**
     * 待确认数量需要同时包含跨项目用户偏好和当前项目候选，供聊天页统一提示用户处理。
     */
    @Test
    void countPendingByUserAndWorkspaceShouldIncludeUserAndCurrentProjectMemories() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("PENDING").toBuilder().id(1L).userId(200L).workspaceId(null).build());
        repository.save(sampleMemory("PENDING").toBuilder().id(2L).userId(200L).workspaceId(300L).memoryScope("PROJECT").build());
        repository.save(sampleMemory("PENDING").toBuilder().id(3L).userId(200L).workspaceId(301L).memoryScope("PROJECT").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        int pendingCount = service.countPendingByUserAndWorkspace(200L, 300L);

        assertEquals(2, pendingCount);
    }

    private GovernanceLongTermMemory sampleMemory(String status) {
        return GovernanceLongTermMemory.builder()
            .memoryScope("USER")
            .userId(200L)
            .workspaceId(300L)
            .memoryKey("memory-key")
            .content("代码风格偏好：业务注释")
            .status(status)
            .keywordJson("[\"业务注释\"]")
            .deleted(0)
            .build();
    }

    private static final class InMemoryLongTermMemoryRepository implements GovernanceLongTermMemoryRepository {
        private final List<GovernanceLongTermMemory> savedMemories = new ArrayList<>();

        @Override
        public void save(GovernanceLongTermMemory memory) {
            savedMemories.removeIf(item -> item.getId() != null && item.getId().equals(memory.getId()));
            savedMemories.add(memory);
        }

        @Override
        public GovernanceLongTermMemory findById(Long id) {
            return savedMemories.stream().filter(memory -> id.equals(memory.getId())).findFirst().orElse(null);
        }

        @Override
        public GovernanceLongTermMemory findByMemoryKey(String memoryKey) {
            return savedMemories.stream().filter(memory -> memoryKey.equals(memory.getMemoryKey())).findFirst().orElse(null);
        }

        @Override
        public List<GovernanceLongTermMemory> findForUser(Long userId, Long workspaceId, String status, int limit) {
            return savedMemories.stream()
                .filter(memory -> userId.equals(memory.getUserId()))
                .filter(memory -> memory.getWorkspaceId() == null || workspaceId != null && workspaceId.equals(memory.getWorkspaceId()))
                .toList();
        }

        @Override
        public List<GovernanceLongTermMemory> findForAdmin(String status, int limit) {
            return savedMemories;
        }

        @Override
        public List<GovernanceLongTermMemory> findActiveForContext(Long userId, Long workspaceId, int limit) {
            return savedMemories.stream()
                .filter(memory -> "ACTIVE".equals(memory.getStatus()))
                .filter(memory -> userId.equals(memory.getUserId()))
                .filter(memory -> memory.getWorkspaceId() == null || workspaceId != null && memory.getWorkspaceId().equals(workspaceId))
                .toList();
        }

        @Override
        public int countPendingByUserAndWorkspace(Long userId, Long workspaceId) {
            return (int) savedMemories.stream()
                .filter(memory -> "PENDING".equals(memory.getStatus()))
                .filter(memory -> userId.equals(memory.getUserId()))
                .filter(memory -> memory.getWorkspaceId() == null || workspaceId == null || workspaceId.equals(memory.getWorkspaceId()))
                .count();
        }
    }
}
