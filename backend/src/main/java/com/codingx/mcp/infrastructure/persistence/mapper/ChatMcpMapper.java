package com.codingx.mcp.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.mcp.infrastructure.persistence.dataobject.ChatMcpDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天 MCP 配置表 Mapper。
 */
@Mapper
public interface ChatMcpMapper extends BaseMapper<ChatMcpDO> {
}
