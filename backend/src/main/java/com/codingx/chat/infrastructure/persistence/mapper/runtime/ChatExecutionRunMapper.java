package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatExecutionRunDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义执行主链路 Mapper 操作。
 */
@Mapper
public interface ChatExecutionRunMapper extends BaseMapper<ChatExecutionRunDO> {
}
