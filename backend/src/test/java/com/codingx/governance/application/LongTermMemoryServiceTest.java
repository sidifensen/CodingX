package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.domain.repository.GovernanceLongTermMemoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 验证长期记忆提取、管理状态和检索回注的确定性规则。
 */
class LongTermMemoryServiceTest {

    /**
     * 显式记忆信号应直接生成 ACTIVE 记忆，并立即参与后续上下文检索。
     */
    @Test
    void extractCandidatesShouldPersistActiveUserMemoryAndRetrieveImmediately() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        LongTermMemoryService service = new LongTermMemoryService(repository);
        ChatConversation conversation = ChatConversation.create(100L, "偏好", 200L, 300L, ChatConversationStatus.ACTIVE);
        ChatMessage userMessage = ChatMessage.userMessage(100L, "请记住我的代码风格偏好：优先写清楚业务注释");
        ChatMessage assistantMessage = ChatMessage.assistantMessage(100L, "已记录，后续会按这个偏好处理。", ChatMessageStatus.COMPLETED, null, null, null);

        List<GovernanceLongTermMemory> memories = service.extractCandidates(conversation, userMessage, assistantMessage);

        assertEquals(1, memories.size());
        GovernanceLongTermMemory memory = memories.getFirst();
        assertEquals("ACTIVE", memory.getStatus());
        assertEquals("USER", memory.getMemoryScope());
        assertEquals(200L, memory.getUserId());
        assertNull(memory.getWorkspaceId());
        assertEquals(100L, memory.getSourceConversationId());
        assertTrue(memory.getContent().contains("优先写清楚业务注释"));
        assertTrue(memory.getKeywordJson().contains("业务注释"));
        assertEquals(
            memory.getId(),
            service.retrieveActiveMemories(200L, 300L, "后续请继续保持业务注释", 10).getFirst().getId()
        );
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
     * 相同 key 的记忆已经存在时不能重复保存，避免记忆列表被同一偏好刷屏。
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
     * 用户管理自己的记忆时应能停用后再启用，只有 ACTIVE 记忆能参与检索回注。
     */
    @Test
    void updateUserMemoryStatusShouldDisableOrReactivateOwnedMemory() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        GovernanceLongTermMemory memory = sampleMemory("ACTIVE").toBuilder().id(500L).userId(200L).build();
        repository.save(memory);
        LongTermMemoryService service = new LongTermMemoryService(repository);

        GovernanceLongTermMemory disabled = service.updateUserMemoryStatus(500L, 200L, "REJECTED");
        GovernanceLongTermMemory reactivated = service.updateUserMemoryStatus(500L, 200L, "ACTIVE");

        assertEquals("REJECTED", disabled.getStatus());
        assertEquals("ACTIVE", reactivated.getStatus());
        assertTrue(reactivated.getUpdatedAt() != null);
    }

    /**
     * 检索只返回当前用户和当前项目范围内的 ACTIVE 记忆，范围过滤仍由仓储层先完成。
     */
    @Test
    void retrieveActiveMemoriesShouldFilterByScopeAndStatus() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(1L).userId(200L).workspaceId(null).content("代码风格偏好：业务注释").keywordJson("[\"业务注释\"]").build());
        repository.save(sampleMemory("REJECTED").toBuilder().id(2L).userId(200L).workspaceId(300L).content("已停用").keywordJson("[\"业务注释\"]").build());
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
     * 当前工作空间内的 ACTIVE 项目记忆即使和问题没有关键词重叠，也应进入上下文候选。
     */
    @Test
    void retrieveActiveMemoriesShouldIncludeCurrentScopeActiveProjectMemoryWithoutQueryOverlap() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE")
            .toBuilder()
            .id(10L)
            .memoryScope("PROJECT")
            .userId(200L)
            .workspaceId(300L)
            .content("仓库归属：斯蒂芬森")
            .keywordJson("[\"斯蒂芬森\"]")
            .build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        List<GovernanceLongTermMemory> memories = service.retrieveActiveMemories(200L, 300L, "随便问一个不相干的问题", 10);

        assertEquals(1, memories.size());
        assertEquals(10L, memories.getFirst().getId());
    }

    /**
     * 当上下文条数有限时，正文或关键词相关的记忆应排在仅因当前作用域生效而回注的记忆前面。
     */
    @Test
    void retrieveActiveMemoriesShouldPrioritizeRelevantMemoriesBeforeGenericScopeMemories() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE")
            .toBuilder()
            .id(20L)
            .memoryScope("PROJECT")
            .userId(200L)
            .workspaceId(300L)
            .content("项目归属：斯蒂芬森")
            .keywordJson("[\"斯蒂芬森\"]")
            .build());
        repository.save(sampleMemory("ACTIVE")
            .toBuilder()
            .id(21L)
            .memoryScope("PROJECT")
            .userId(200L)
            .workspaceId(300L)
            .content("测试框架使用 JUnit 5")
            .keywordJson("[\"JUnit 5\"]")
            .build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        List<GovernanceLongTermMemory> memories = service.retrieveActiveMemories(200L, 300L, "这次测试框架继续使用 JUnit 5", 2);

        assertEquals(2, memories.size());
        assertEquals(21L, memories.get(0).getId());
        assertEquals(20L, memories.get(1).getId());
    }

    /**
     * 回注检索只应打印单条汇总日志，避免同一轮聊天同时出现“开始”和“结果”两条重复 INFO。
     */
    @Test
    void retrieveActiveMemoriesShouldLogContextLoadOnceWithSummary() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(30L).userId(200L).workspaceId(300L).content("测试框架使用 JUnit 5").keywordJson("[\"JUnit 5\"]").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);
        Logger logger = (Logger) LoggerFactory.getLogger(LongTermMemoryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.retrieveActiveMemories(200L, 300L, "这次测试框架继续使用 JUnit 5", 6);
        } finally {
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
        long contextLogCount = appender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("长期记忆回注"))
            .count();
        assertEquals(1L, contextLogCount);
        assertTrue(logs.contains("长期记忆回注已加载"));
        assertFalse(logs.contains("userId=200"));
        assertFalse(logs.contains("workspaceId=300"));
        assertTrue(logs.contains("candidateCount=1"));
        assertTrue(logs.contains("selectedCount=1"));
        // 查询语句命中关键词 "JUnit 5"，排序原因应为 KEYWORD 而非默认的 CURRENT_SCOPE_ACTIVE。
        assertTrue(logs.contains("matchReasons=[KEYWORD]"));
        assertFalse(logs.contains("memoryIds="));
        assertFalse(logs.contains("长期记忆回注开始"));
        assertFalse(logs.contains("长期记忆回注结果"));
    }

    /**
     * 用户记忆管理页需要查看当前账号所有工作空间的项目记忆，不能只局限于当前选中的 workspace。
     */
    @Test
    void listUserMemoriesWithAllWorkspacesShouldIncludeProjectMemoriesAcrossWorkspaces() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(1L).userId(200L).workspaceId(null).content("用户偏好").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(2L).userId(200L).workspaceId(300L).memoryScope("PROJECT").content("项目 300 约定").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(3L).userId(200L).workspaceId(301L).memoryScope("PROJECT").content("项目 301 约定").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(4L).userId(201L).workspaceId(301L).memoryScope("PROJECT").content("其他用户项目约定").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        List<GovernanceLongTermMemory> memories = service.listUserMemories(200L, null, "ALL", true);

        assertEquals(3, memories.size());
        assertTrue(memories.stream().anyMatch(memory -> "项目 300 约定".equals(memory.getContent())));
        assertTrue(memories.stream().anyMatch(memory -> "项目 301 约定".equals(memory.getContent())));
        assertTrue(memories.stream().noneMatch(memory -> "其他用户项目约定".equals(memory.getContent())));
    }

    /**
     * 已生效数量需要同时包含跨项目用户偏好和当前项目记忆，供聊天页展示 Agent 当前上下文规模。
     */
    @Test
    void countActiveByUserAndWorkspaceShouldIncludeUserAndCurrentProjectMemories() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(1L).userId(200L).workspaceId(null).build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(2L).userId(200L).workspaceId(300L).memoryScope("PROJECT").build());
        repository.save(sampleMemory("ACTIVE").toBuilder().id(3L).userId(200L).workspaceId(301L).memoryScope("PROJECT").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        int activeCount = service.countActiveByUserAndWorkspace(200L, 300L);

        assertEquals(2, activeCount);
    }

    /**
     * 用户编辑自己的记忆时应刷新正文、关键词、去重键与更新时间，保证后续模型回注使用最新约定。
     */
    @Test
    void updateUserMemoryContentShouldRefreshContentKeywordsAndMemoryKey() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        GovernanceLongTermMemory memory = sampleMemory("ACTIVE")
            .toBuilder()
            .id(500L)
            .userId(200L)
            .memoryKey("old-key")
            .content("旧记忆")
            .keywordJson("[\"旧记忆\"]")
            .build();
        repository.save(memory);
        LongTermMemoryService service = new LongTermMemoryService(repository);

        GovernanceLongTermMemory updated = service.updateUserMemoryContent(500L, 200L, "新的项目注释规范：说明业务意图");

        assertEquals("新的项目注释规范：说明业务意图", updated.getContent());
        assertTrue(updated.getKeywordJson().contains("业务意图"));
        assertTrue(updated.getMemoryKey().startsWith("user:"));
        assertTrue(!"old-key".equals(updated.getMemoryKey()));
        assertNotNull(updated.getUpdatedAt());
    }

    /**
     * 空白正文不能保存，避免把无意义内容回注给模型。
     */
    @Test
    void updateUserMemoryContentShouldRejectBlankContent() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(500L).userId(200L).build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateUserMemoryContent(500L, 200L, "   ")
        );

        assertEquals("GOVERNANCE_MEMORY_CONTENT_REQUIRED", exception.getCode());
    }

    /**
     * 用户不能编辑或删除他人记忆，归属校验必须在服务层统一完成。
     */
    @Test
    void userMemoryMutationsShouldRejectForeignMemory() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(500L).userId(201L).build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        BusinessException editException = assertThrows(
            BusinessException.class,
            () -> service.updateUserMemoryContent(500L, 200L, "新的约定")
        );
        BusinessException deleteException = assertThrows(
            BusinessException.class,
            () -> service.deleteUserMemory(500L, 200L)
        );

        assertEquals("GOVERNANCE_MEMORY_FORBIDDEN", editException.getCode());
        assertEquals("GOVERNANCE_MEMORY_FORBIDDEN", deleteException.getCode());
    }

    /**
     * 用户删除记忆时只做逻辑删除，后续列表与上下文检索都应排除该记录。
     */
    @Test
    void deleteUserMemoryShouldMarkDeletedAndExcludeFromContext() {
        InMemoryLongTermMemoryRepository repository = new InMemoryLongTermMemoryRepository();
        repository.save(sampleMemory("ACTIVE").toBuilder().id(500L).userId(200L).workspaceId(null).keywordJson("[\"业务注释\"]").build());
        LongTermMemoryService service = new LongTermMemoryService(repository);

        service.deleteUserMemory(500L, 200L);

        assertEquals(1, repository.findById(500L).getDeleted());
        assertTrue(service.retrieveActiveMemories(200L, null, "业务注释", 10).isEmpty());
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
        public List<GovernanceLongTermMemory> findAllForUser(Long userId, String status, int limit) {
            return savedMemories.stream()
                .filter(memory -> userId.equals(memory.getUserId()))
                .filter(memory -> status == null || status.equals(memory.getStatus()))
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
                .filter(memory -> memory.getDeleted() == null || memory.getDeleted() == 0)
                .filter(memory -> userId.equals(memory.getUserId()))
                .filter(memory -> memory.getWorkspaceId() == null || workspaceId != null && memory.getWorkspaceId().equals(workspaceId))
                .toList();
        }

        @Override
        public int countActiveByUserAndWorkspace(Long userId, Long workspaceId) {
            return (int) savedMemories.stream()
                .filter(memory -> "ACTIVE".equals(memory.getStatus()))
                .filter(memory -> userId.equals(memory.getUserId()))
                .filter(memory -> memory.getWorkspaceId() == null || workspaceId == null || workspaceId.equals(memory.getWorkspaceId()))
                .count();
        }
    }
}
