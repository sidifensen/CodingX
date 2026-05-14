package com.codingx.task.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.task.infrastructure.persistence.dataobject.TaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 TaskMapper 的 Mapper 操作。
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {
}
