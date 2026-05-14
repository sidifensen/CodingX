package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageReferenceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义参考来源 Mapper 操作。
 */
@Mapper
public interface ChatMessageReferenceMapper extends BaseMapper<ChatMessageReferenceDO> {
}
