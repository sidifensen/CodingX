package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.TaskSkillDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义任务技能绑定表 Mapper。
 */
@Mapper
public interface TaskSkillMapper extends BaseMapper<TaskSkillDO> {
}
