package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatGoalEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天目标事件表 Mapper 操作。
 */
@Mapper
public interface ChatGoalEventMapper extends BaseMapper<ChatGoalEventDO> {
}
