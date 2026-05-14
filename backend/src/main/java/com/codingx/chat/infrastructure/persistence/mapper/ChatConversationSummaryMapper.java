package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationSummaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义会话摘要 Mapper 操作。
 */
@Mapper
public interface ChatConversationSummaryMapper extends BaseMapper<ChatConversationSummaryDO> {
}
