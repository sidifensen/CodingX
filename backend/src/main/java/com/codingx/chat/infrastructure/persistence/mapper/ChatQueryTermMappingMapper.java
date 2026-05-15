package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatQueryTermMappingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义查询词映射表的 MyBatis Mapper。
 */
@Mapper
public interface ChatQueryTermMappingMapper extends BaseMapper<ChatQueryTermMappingDO> {
}
