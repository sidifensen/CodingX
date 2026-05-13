package com.codingx.backend.event.domain.repository;

import com.codingx.backend.event.domain.model.TaskEvent;
import java.util.List;

public interface TaskEventRepository {

    void save(TaskEvent taskEvent);

    List<TaskEvent> findByTaskId(Long taskId);

    long nextSequence(Long taskId);
}