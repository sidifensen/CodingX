package com.codingx.automation.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.automation.infrastructure.persistence.dataobject.AutomationTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自动化任务 Mapper，负责执行 automation_task 表基础读写。
 */
@Mapper
public interface AutomationTaskMapper extends BaseMapper<AutomationTaskDO> {
}
