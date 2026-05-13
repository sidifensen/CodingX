package com.codingx.backend.event.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.backend.event.domain.model.TaskEvent;
import com.codingx.backend.event.domain.repository.TaskEventRepository;
import com.codingx.backend.event.infrastructure.persistence.dataobject.TaskEventDO;
import com.codingx.backend.event.infrastructure.persistence.mapper.TaskEventMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the persistence behavior required by TaskEventRepositoryImpl.
 */
@Repository
@RequiredArgsConstructor
public class TaskEventRepositoryImpl implements TaskEventRepository {

    /**
     * taskEventMapper value.
     */
    private final TaskEventMapper taskEventMapper;

    /**
     * Persists the state handled by save.
     * @param taskEvent input argument.
     */
    @Override
    public void save(TaskEvent taskEvent) {
        TaskEventDO dataObject = new TaskEventDO();
        dataObject.setId(taskEvent.getId());
        dataObject.setTaskId(taskEvent.getTaskId());
        dataObject.setEventType(taskEvent.getEventType());
        dataObject.setSequenceNo(taskEvent.getSequenceNo());
        dataObject.setTitle(taskEvent.getTitle());
        dataObject.setContent(taskEvent.getContent());
        dataObject.setMetadataJson(taskEvent.getMetadataJson());
        dataObject.setCreatedAt(taskEvent.getCreatedAt());
        taskEventMapper.insert(dataObject);
    }

    /**
     * Finds the data required by findByTaskId.
     * @param taskId input argument.
     * @return processing result.
     */
    @Override
    public List<TaskEvent> findByTaskId(Long taskId) {
        return taskEventMapper.selectList(new LambdaQueryWrapper<TaskEventDO>()
                .eq(TaskEventDO::getTaskId, taskId)
                .orderByAsc(TaskEventDO::getSequenceNo))
            .stream()
            .map(dataObject -> TaskEvent.create(dataObject.getTaskId(), dataObject.getEventType(), dataObject.getSequenceNo(), dataObject.getTitle(), dataObject.getContent(), dataObject.getMetadataJson()))
            .toList();
    }

    /**
     * Executes the logic defined by nextSequence.
     * @param taskId input argument.
     * @return processing result.
     */
    @Override
    public long nextSequence(Long taskId) {
        TaskEventDO latest = taskEventMapper.selectOne(new LambdaQueryWrapper<TaskEventDO>()
            .eq(TaskEventDO::getTaskId, taskId)
            .orderByDesc(TaskEventDO::getSequenceNo)
            .last("limit 1"));
        return latest == null ? 1L : latest.getSequenceNo() + 1L;
    }
}
