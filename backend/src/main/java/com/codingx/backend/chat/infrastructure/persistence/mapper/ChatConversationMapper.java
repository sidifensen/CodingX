package com.codingx.backend.chat.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.backend.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Declares mapper operations used by ChatConversationMapper.
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
}
