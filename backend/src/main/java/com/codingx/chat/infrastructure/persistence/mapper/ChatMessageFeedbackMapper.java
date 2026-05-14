package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageFeedbackDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义消息反馈 Mapper 操作。
 */
@Mapper
public interface ChatMessageFeedbackMapper extends BaseMapper<ChatMessageFeedbackDO> {
}
