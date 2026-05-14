package com.codingx.event.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.event.domain.model.TaskEvent;
import com.codingx.event.domain.repository.TaskEventRepository;
import com.codingx.event.infrastructure.persistence.dataobject.TaskEventDO;
import com.codingx.event.infrastructure.persistence.mapper.TaskEventMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 TaskEventRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class TaskEventRepositoryImpl implements TaskEventRepository {

    /**
     * TaskEventMapper 依赖。
     */
    private final TaskEventMapper taskEventMapper;

    /**
     * 持久化 save 处理的状态。
     * @param taskEvent 输入参数。
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
     * 查询 findByTaskId 需要的数据。
     * @param taskId 输入参数。
     * @return 输入参数。
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
     * 执行 nextSequence 定义的处理逻辑。
     * @param taskId 输入参数。
     * @return 输入参数。
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
