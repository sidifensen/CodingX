package com.codingx.workspace.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 WorkspaceMapper 的 Mapper 操作。
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceDO> {
}
