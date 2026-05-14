package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentExampleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义意图示例 Mapper 操作。
 */
@Mapper
public interface ChatIntentExampleMapper extends BaseMapper<ChatIntentExampleDO> {
}
