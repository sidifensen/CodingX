package com.codingx.chat.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话 Mapper，继承 MyBatis-Plus 通用 CRUD 能力，负责读写 chat_conversation 表。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
}
