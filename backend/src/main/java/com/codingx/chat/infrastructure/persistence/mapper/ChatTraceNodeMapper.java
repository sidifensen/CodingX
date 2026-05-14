package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceNodeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 Trace 节点 Mapper 操作。
 */
@Mapper
public interface ChatTraceNodeMapper extends BaseMapper<ChatTraceNodeDO> {
}
