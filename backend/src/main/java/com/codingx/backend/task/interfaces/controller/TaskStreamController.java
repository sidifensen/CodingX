package com.codingx.backend.task.interfaces.controller;

import com.codingx.backend.task.infrastructure.stream.TaskSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskStreamController {

    private final TaskSseRegistry taskSseRegistry;

    @GetMapping("/{taskId}/stream")
    public SseEmitter stream(@PathVariable Long taskId) {
        return taskSseRegistry.register(taskId);
    }
}