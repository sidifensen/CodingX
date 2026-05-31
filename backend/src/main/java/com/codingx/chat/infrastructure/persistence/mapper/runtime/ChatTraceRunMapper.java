package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Trace 根链路 Mapper，继承 MyBatis-Plus 通用 CRUD 能力，负责读写 chat_trace_run 表。
 */
@Mapper
public interface ChatTraceRunMapper extends BaseMapper<ChatTraceRunDO> {
}
