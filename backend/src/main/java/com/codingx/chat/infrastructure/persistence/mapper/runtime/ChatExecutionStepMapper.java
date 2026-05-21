package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatExecutionStepDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义执行步骤 Mapper 操作。
 */
@Mapper
public interface ChatExecutionStepMapper extends BaseMapper<ChatExecutionStepDO> {
}
