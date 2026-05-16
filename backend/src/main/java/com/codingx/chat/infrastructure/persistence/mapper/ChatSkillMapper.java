package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatSkillDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天技能配置表 Mapper。
 */
@Mapper
public interface ChatSkillMapper extends BaseMapper<ChatSkillDO> {
}
