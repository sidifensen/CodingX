package com.codingx.mcp.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.mcp.infrastructure.persistence.dataobject.TaskMcpDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 MCP 绑定表 Mapper。
 */
@Mapper
public interface TaskMcpMapper extends BaseMapper<TaskMcpDO> {
}
