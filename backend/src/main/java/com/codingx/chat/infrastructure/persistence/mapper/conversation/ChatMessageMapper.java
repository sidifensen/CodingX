package com.codingx.chat.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 ChatMessageMapper 的 Mapper 操作。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
