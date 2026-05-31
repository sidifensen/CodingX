package com.codingx.chat.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper，继承 MyBatis-Plus 通用 CRUD 能力，负责读写 chat_message 表。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
