package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 Trace 根链路 Mapper 操作。
 */
@Mapper
public interface ChatTraceRunMapper extends BaseMapper<ChatTraceRunDO> {
}
