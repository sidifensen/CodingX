package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageArtifactDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天产物 Mapper 操作。
 */
@Mapper
public interface ChatMessageArtifactMapper extends BaseMapper<ChatMessageArtifactDO> {
}
