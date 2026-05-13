package com.codingx.backend.task.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void startMovesTaskFromCreatedToRunning() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);

        task.start();

        assertEquals(TaskStatus.RUNNING, task.getStatus());
    }

    @Test
    void completeRequiresRunningStatus() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);

        assertThrows(IllegalStateException.class, () -> task.complete("done"));
    }

    @Test
    void failStoresErrorAndMovesTaskToFailed() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        task.start();

        task.fail("boom");

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("boom", task.getErrorMessage());
    }
}
