package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.chat.application.service.goal.ChatGoalService;
import com.codingx.chat.application.service.goal.ChatGoalView;
import com.codingx.chat.domain.model.ChatGoal;
import com.codingx.chat.domain.model.ChatGoalStep;
import com.codingx.chat.domain.model.ChatGoalStatus;
import com.codingx.chat.domain.model.ChatGoalStepStatus;
import com.codingx.chat.domain.repository.ChatGoalRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证会话级真实目标应用服务，确保目标、步骤和事件都以数据库仓储为事实来源。
 */
class ChatGoalServiceTest {

    /** 记录型仓储替身，用于观察服务层写入目标、步骤和事件的真实数据。 */
    private RecordingChatGoalRepository chatGoalRepository;

    /** 被测目标服务，负责会话归属校验、目标创建更新和事件追加。 */
    private ChatGoalService chatGoalService;

    /**
     * 每个用例使用全新的仓储替身，避免跨用例目标状态互相污染。
     */
    @BeforeEach
    void setUp() {
        chatGoalRepository = new RecordingChatGoalRepository();
        chatGoalService = new ChatGoalService(chatGoalRepository);
    }

    /**
     * createGoal 必须按 conversationId/userId 绑定目标，并把步骤独立保存。
     */
    @Test
    void createGoalPersistsConversationBoundGoalAndSteps() {
        ChatGoalView view = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(
                null,
                "default",
                "实现真实目标模式",
                "后端持久化目标状态",
                List.of(new ChatGoalService.StepCommand("schema", "补齐数据库表", "PENDING", "先建表"))
            )
        );

        assertEquals("GOAL_CREATED", view.eventType());
        assertEquals(1001L, chatGoalRepository.savedGoals.getFirst().getConversationId());
        assertEquals(2001L, chatGoalRepository.savedGoals.getFirst().getUserId());
        assertEquals(3001L, chatGoalRepository.savedGoals.getFirst().getCreatedRunId());
        assertEquals(ChatGoalStatus.ACTIVE, chatGoalRepository.savedGoals.getFirst().getStatus());
        assertEquals("schema", chatGoalRepository.savedSteps.getFirst().getStepKey());
        assertEquals(ChatGoalStepStatus.PENDING, chatGoalRepository.savedSteps.getFirst().getStatus());
        assertEquals("GOAL_CREATED", chatGoalRepository.events.getFirst().eventType());
        assertEquals(view.id(), String.valueOf(chatGoalRepository.savedGoals.getFirst().getId()));
    }

    /**
     * 创建目标后必须发布 goal SSE 事件，前端收到后可直接刷新 active goal 浮窗。
     */
    @Test
    void createGoalPublishesGoalEvent() {
        RecordingChatStreamPublisher publisher = new RecordingChatStreamPublisher();
        chatGoalService = new ChatGoalService(chatGoalRepository, publisher);

        ChatGoalView view = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        assertEquals(1001L, publisher.conversationId);
        assertEquals(view, publisher.payload);
    }

    /**
     * 同一会话已有 ACTIVE 目标时再次创建应返回既有目标，不能重复插入第二个活跃目标。
     */
    @Test
    void createGoalReturnsExistingActiveGoalWithoutDuplicating() {
        ChatGoalView first = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "第一个目标", null, List.of())
        );

        ChatGoalView second = chatGoalService.createGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.CreateGoalCommand(null, "default", "第二个目标", null, List.of())
        );

        assertEquals(first.id(), second.id());
        assertEquals(1, chatGoalRepository.savedGoals.size());
        assertEquals("第一个目标", second.title());
    }

    /**
     * updateGoal 必须更新主目标、替换步骤快照并追加目标事件。
     */
    @Test
    void updateGoalPersistsStatusStepsAndEvent() {
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        ChatGoalView updated = chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                created.id(),
                null,
                "目标",
                null,
                "COMPLETED",
                "已经完成",
                List.of(new ChatGoalService.StepCommand("schema", "数据库", "COMPLETED", "已落库"))
            )
        );

        assertEquals(ChatGoalStatus.COMPLETED, chatGoalRepository.goals.get(created.id()).getStatus());
        assertEquals("已经完成", chatGoalRepository.goals.get(created.id()).getProgressSummary());
        assertEquals(ChatGoalStepStatus.COMPLETED, chatGoalRepository.stepsByGoalId.get(created.id()).getFirst().getStatus());
        assertTrue(updated.completedAt() != null);
        assertEquals("GOAL_COMPLETED", chatGoalRepository.events.getLast().eventType());
    }

    /**
     * 模型可能把默认目标键写入 goalId 字段，服务层必须把非数字 goalId 兼容为稳定键继续更新。
     */
    @Test
    void updateGoalTreatsNonNumericGoalIdAsGoalKey() {
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        ChatGoalView updated = chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                "default",
                null,
                "目标",
                null,
                "ACTIVE",
                "完成第一步",
                List.of(new ChatGoalService.StepCommand("draft", "起草 HTML", "COMPLETED", "已完成结构草稿"))
            )
        );

        assertEquals(created.id(), updated.id());
        assertEquals("完成第一步", chatGoalRepository.goals.get(created.id()).getProgressSummary());
        assertEquals(ChatGoalStepStatus.COMPLETED, chatGoalRepository.stepsByGoalId.get(created.id()).getFirst().getStatus());
        assertEquals("GOAL_UPDATED", chatGoalRepository.events.getLast().eventType());
    }

    /**
     * update_goal 若收到模型编造的非数字 goalId，应回退当前会话 active goal，避免已创建目标后再次报“目标不存在”。
     */
    @Test
    void updateGoalFallsBackToActiveGoalWhenNonNumericGoalIdMisses() {
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        ChatGoalView updated = chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                "goal_202606091110",
                null,
                null,
                null,
                "ACTIVE",
                "已完成结构设计",
                List.of(new ChatGoalService.StepCommand("draft", "起草 HTML", "COMPLETED", "已完成结构草稿"))
            )
        );

        assertEquals(created.id(), updated.id());
        assertEquals("已完成结构设计", chatGoalRepository.goals.get(created.id()).getProgressSummary());
        assertEquals("GOAL_UPDATED", chatGoalRepository.events.getLast().eventType());
    }

    /**
     * getGoal 必须按当前会话读取目标，不能只按 goalKey 跨会话命中同名目标。
     */
    @Test
    void getGoalIsIsolatedByConversationIdAndUserId() {
        ChatGoalView first = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "会话一目标", null, List.of())
        );
        ChatGoalView second = chatGoalService.createGoal(
            1002L,
            2001L,
            3002L,
            new ChatGoalService.CreateGoalCommand(null, "default", "会话二目标", null, List.of())
        );

        Optional<ChatGoalView> loadedFirst = chatGoalService.getGoal(1001L, 2001L, null, "default");
        Optional<ChatGoalView> loadedSecond = chatGoalService.getGoal(1002L, 2001L, null, "default");

        assertTrue(loadedFirst.isPresent());
        assertTrue(loadedSecond.isPresent());
        assertEquals(first.id(), loadedFirst.orElseThrow().id());
        assertEquals(second.id(), loadedSecond.orElseThrow().id());
        assertNotEquals(loadedFirst.orElseThrow().id(), loadedSecond.orElseThrow().id());
    }

    /**
     * 步骤未全部完成时，模型传入 COMPLETED 必须被强制降级为 ACTIVE，防止提前标完目标。
     */
    @Test
    void updateGoalDowngradesToActiveWhenStepsNotAllCompleted() {
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        // 发送 3 步：1 个 COMPLETED，2 个 PENDING，但目标状态声称 COMPLETED
        ChatGoalView updated = chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                created.id(),
                null,
                null,
                null,
                "COMPLETED",
                "进行中",
                List.of(
                    new ChatGoalService.StepCommand("step1", "步骤一", "COMPLETED", "已完成"),
                    new ChatGoalService.StepCommand("step2", "步骤二", "PENDING", "等待"),
                    new ChatGoalService.StepCommand("step3", "步骤三", "PENDING", "等待")
                )
            )
        );

        // 步骤未全部完成，必须降级为 ACTIVE
        assertEquals(ChatGoalStatus.ACTIVE, chatGoalRepository.goals.get(created.id()).getStatus());
        assertEquals("GOAL_UPDATED", chatGoalRepository.events.getLast().eventType());
        assertEquals(null, updated.completedAt());
    }

    /**
     * 所有步骤均为 COMPLETED 时，模型传入 COMPLETED 必须原样接受。
     */
    @Test
    void updateGoalAcceptsCompletionWhenAllStepsDone() {
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        // 全部步骤均为 COMPLETED，目标状态 COMPLETED 应通过
        ChatGoalView updated = chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                created.id(),
                null,
                null,
                null,
                "COMPLETED",
                "全部完成",
                List.of(
                    new ChatGoalService.StepCommand("step1", "步骤一", "COMPLETED", "已完成"),
                    new ChatGoalService.StepCommand("step2", "步骤二", "COMPLETED", "已完成")
                )
            )
        );

        assertEquals(ChatGoalStatus.COMPLETED, chatGoalRepository.goals.get(created.id()).getStatus());
        assertEquals("GOAL_COMPLETED", chatGoalRepository.events.getLast().eventType());
        assertTrue(updated.completedAt() != null);
    }

    /**
     * 目标无步骤时，模型传入 COMPLETED 必须降级为 ACTIVE，步骤为空等同于"未完成"。
     */
    @Test
    void updateGoalDowngradesToActiveWhenNoStepsExist() {
        // 创建无步骤的目标
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "空步骤目标", null, List.of())
        );

        // 不传步骤，直接声称 COMPLETED
        ChatGoalView updated = chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                created.id(),
                null,
                null,
                null,
                "COMPLETED",
                "无步骤完成",
                List.of()
            )
        );

        // 无步骤时 areAllStepsCompleted 返回 false，必须降级为 ACTIVE
        assertEquals(ChatGoalStatus.ACTIVE, chatGoalRepository.goals.get(created.id()).getStatus());
        assertEquals("GOAL_UPDATED", chatGoalRepository.events.getLast().eventType());
        assertNull(updated.completedAt());
    }

    /**
     * BLOCKED 和 CANCELLED 状态不受步骤完成校验影响，可直接设置。
     */
    @Test
    void updateGoalAllowsBlockedAndCancelledRegardlessOfSteps() {
        ChatGoalView created = chatGoalService.createGoal(
            1001L,
            2001L,
            3001L,
            new ChatGoalService.CreateGoalCommand(null, "default", "目标", null, List.of())
        );

        // 步骤未完成，但请求 BLOCKED — 应直接接受
        chatGoalService.updateGoal(
            1001L,
            2001L,
            3002L,
            new ChatGoalService.UpdateGoalCommand(
                created.id(),
                null,
                null,
                null,
                "BLOCKED",
                "被阻塞",
                List.of(
                    new ChatGoalService.StepCommand("step1", "步骤一", "PENDING", "等待")
                )
            )
        );
        assertEquals(ChatGoalStatus.BLOCKED, chatGoalRepository.goals.get(created.id()).getStatus());

        // 再请求 CANCELLED — 应直接接受
        chatGoalService.updateGoal(
            1001L,
            2001L,
            3003L,
            new ChatGoalService.UpdateGoalCommand(
                created.id(),
                null,
                null,
                null,
                "CANCELLED",
                "已取消",
                List.of(
                    new ChatGoalService.StepCommand("step1", "步骤一", "PENDING", "等待")
                )
            )
        );
        assertEquals(ChatGoalStatus.CANCELLED, chatGoalRepository.goals.get(created.id()).getStatus());
    }

    /**
     * 记录型目标仓储只保存服务层写入结果，模拟 MyBatis 仓储应提供的最小行为。
     */
    private static final class RecordingChatGoalRepository implements ChatGoalRepository {

        /** 按字符串 ID 保存目标，便于测试读取服务生成的 Snowflake 主键。 */
        private final Map<String, ChatGoal> goals = new LinkedHashMap<>();
        /** 按目标 ID 保存步骤快照，模拟步骤表独立存储。 */
        private final Map<String, List<ChatGoalStep>> stepsByGoalId = new LinkedHashMap<>();
        /** 记录每次保存目标的调用，用于断言是否重复创建。 */
        private final List<ChatGoal> savedGoals = new ArrayList<>();
        /** 记录最近保存的步骤，便于断言步骤状态规范化。 */
        private final List<ChatGoalStep> savedSteps = new ArrayList<>();
        /** 记录追加事件，验证 create/update 都写入审计流水。 */
        private final List<ChatGoalRepository.ChatGoalEventRecord> events = new ArrayList<>();

        @Override
        public Optional<ChatGoal> findActiveByConversationIdAndUserId(Long conversationId, Long userId) {
            return goals.values().stream()
                .filter(goal -> conversationId.equals(goal.getConversationId()))
                .filter(goal -> userId.equals(goal.getUserId()))
                .filter(goal -> goal.getStatus() == ChatGoalStatus.ACTIVE)
                .findFirst();
        }

        @Override
        public Optional<ChatGoal> findLatestByConversationIdAndUserId(Long conversationId, Long userId) {
            // 与 MyBatis 实现保持一致：不过滤状态，按更新时间倒序取最新目标，更新时间相同按 ID 倒序。
            return goals.values().stream()
                .filter(goal -> conversationId.equals(goal.getConversationId()))
                .filter(goal -> userId.equals(goal.getUserId()))
                .max(Comparator.comparing(ChatGoal::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(ChatGoal::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
        }

        @Override
        public Optional<ChatGoal> findByIdAndConversationIdAndUserId(Long goalId, Long conversationId, Long userId) {
            return Optional.ofNullable(goals.get(String.valueOf(goalId)))
                .filter(goal -> conversationId.equals(goal.getConversationId()))
                .filter(goal -> userId.equals(goal.getUserId()));
        }

        @Override
        public Optional<ChatGoal> findByGoalKeyAndConversationIdAndUserId(String goalKey, Long conversationId, Long userId) {
            return goals.values().stream()
                .filter(goal -> goalKey.equals(goal.getGoalKey()))
                .filter(goal -> conversationId.equals(goal.getConversationId()))
                .filter(goal -> userId.equals(goal.getUserId()))
                .findFirst();
        }

        @Override
        public List<ChatGoal> findAllByConversationId(Long conversationId) {
            return goals.values().stream()
                .filter(goal -> conversationId.equals(goal.getConversationId()))
                .toList();
        }

        @Override
        public void saveGoal(ChatGoal goal) {
            savedGoals.add(goal);
            goals.put(String.valueOf(goal.getId()), goal);
        }

        @Override
        public List<ChatGoalStep> findStepsByGoalId(Long goalId) {
            return stepsByGoalId.getOrDefault(String.valueOf(goalId), List.of());
        }

        @Override
        public List<ChatGoalStep> findStepsByGoalIds(List<Long> goalIds) {
            if (goalIds == null || goalIds.isEmpty()) {
                return List.of();
            }
            return goalIds.stream()
                .flatMap(goalId -> findStepsByGoalId(goalId).stream())
                .toList();
        }

        @Override
        public void replaceSteps(Long goalId, List<ChatGoalStep> steps, LocalDateTime now) {
            savedSteps.clear();
            savedSteps.addAll(steps);
            stepsByGoalId.put(String.valueOf(goalId), new ArrayList<>(steps));
        }

        @Override
        public void appendEvent(ChatGoalRepository.ChatGoalEventRecord eventRecord) {
            events.add(eventRecord);
        }

        @Override
        public List<ChatGoalRepository.ChatGoalEventRecord> findEventsByConversationId(Long conversationId) {
            return events.stream()
                .filter(event -> conversationId.equals(event.conversationId()))
                .toList();
        }
    }

    /**
     * 记录型流式发布器，仅捕获目标事件，其他聊天事件在服务层测试中不关心。
     */
    private static final class RecordingChatStreamPublisher implements com.codingx.chat.domain.port.ChatStreamPublisher {

        /** 最近一次发布目标事件的会话 ID。 */
        private Long conversationId;
        /** 最近一次发布目标事件的载荷。 */
        private Object payload;

        @Override public void publishUserMessage(Long conversationId, String content) { }
        @Override public void publishAssistantDelta(Long conversationId, String delta) { }
        @Override public void publishAssistantThinkingDelta(Long conversationId, String delta) { }
        @Override public void publishStep(Long conversationId, Object payload) { }
        @Override public void publishMcpCall(Long conversationId, Object payload) { }
        @Override public void publishToolCall(Long conversationId, Object payload) { }
        @Override public void publishReference(Long conversationId, Object payload) { }
        @Override public void publishArtifact(Long conversationId, Object payload) { }

        @Override
        public void publishGoal(Long conversationId, Object payload) {
            this.conversationId = conversationId;
            this.payload = payload;
        }

        @Override public void publishAssistantCompleted(Long conversationId, String content, String title) { }
        @Override public void publishCancelled(Long conversationId) { }
        @Override public void publishRejected(Long conversationId, String reason) { }
        @Override public void publishQueued(Long conversationId, int position) { }
        @Override public void publishQueueAccepted(Long conversationId) { }
        @Override public void publishError(Long conversationId, String message) { }
    }
}
