package com.codingx.task.domain.model;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * 验证 Task 的关键场景。
 */
class TaskTest {

    /**
     * 启动 startMovesTaskFromCreatedToRunning 处理的流程。
     */
    @Test
    void startMovesTaskFromCreatedToRunning() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        task.start();
        assertEquals(TaskStatus.RUNNING, task.getStatus());
    }

    /**
     * 创建任务时应保留绑定技能编码，供后续运行链路读取。
     */
    @Test
    void createStoresSkillCodes() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L, java.util.List.of("conversation-core", "web-search"));
        assertEquals(java.util.List.of("conversation-core", "web-search"), task.getSkillCodes());
    }

    /**
     * 将 completeRequiresRunningStatus 处理的流程标记为完成。
     */
    @Test
    void completeRequiresRunningStatus() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        assertThrows(IllegalStateException.class, () -> task.complete("done"));
    }

    /**
     * 将 failStoresErrorAndMovesTaskToFailed 处理的流程标记为失败。
     */
    @Test
    void failStoresErrorAndMovesTaskToFailed() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        task.start();
        task.fail("boom");
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("boom", task.getErrorMessage());
    }
}
