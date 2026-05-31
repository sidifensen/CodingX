package com.codingx.workspace.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间表 Mapper，继承 MyBatis-Plus 通用 CRUD 能力。
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceDO> {
}
