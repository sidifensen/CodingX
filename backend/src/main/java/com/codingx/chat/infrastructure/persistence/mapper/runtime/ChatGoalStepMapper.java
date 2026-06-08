package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatGoalStepDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天目标步骤表 Mapper 操作。
 */
@Mapper
public interface ChatGoalStepMapper extends BaseMapper<ChatGoalStepDO> {
}
