package com.codingx.backend.task.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.backend.task.infrastructure.persistence.dataobject.TaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Declares mapper operations used by TaskMapper.
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {
}
