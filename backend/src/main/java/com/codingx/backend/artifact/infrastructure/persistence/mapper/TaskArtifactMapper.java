package com.codingx.backend.artifact.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.backend.artifact.infrastructure.persistence.dataobject.TaskArtifactDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskArtifactMapper extends BaseMapper<TaskArtifactDO> {
}