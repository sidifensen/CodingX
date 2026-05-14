package com.codingx.chat.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 ChatConversationMapper 的 Mapper 操作。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
}
