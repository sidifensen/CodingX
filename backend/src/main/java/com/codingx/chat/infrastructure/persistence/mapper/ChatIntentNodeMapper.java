package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentNodeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义意图树节点 Mapper 操作。
 */
@Mapper
public interface ChatIntentNodeMapper extends BaseMapper<ChatIntentNodeDO> {
}
