package com.codingx.event.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.event.infrastructure.persistence.dataobject.TaskEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 TaskEventMapper 的 Mapper 操作。
 */
@Mapper
public interface TaskEventMapper extends BaseMapper<TaskEventDO> {
}
