package com.codingx.task.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.task.infrastructure.persistence.dataobject.TaskArtifactDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义任务产物的 Mapper 操作。
 */
@Mapper
public interface TaskArtifactMapper extends BaseMapper<TaskArtifactDO> {
}
