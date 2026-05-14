package com.codingx.artifact.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.artifact.infrastructure.persistence.dataobject.TaskArtifactDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 TaskArtifactMapper 的 Mapper 操作。
 */
@Mapper
public interface TaskArtifactMapper extends BaseMapper<TaskArtifactDO> {
}
