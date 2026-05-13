package com.codingx.backend.event.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.backend.event.infrastructure.persistence.dataobject.TaskEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskEventMapper extends BaseMapper<TaskEventDO> {
}