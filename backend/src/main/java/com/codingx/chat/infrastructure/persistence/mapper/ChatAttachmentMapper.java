package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天附件 Mapper 操作。
 */
@Mapper
public interface ChatAttachmentMapper extends BaseMapper<ChatAttachmentDO> {
}
