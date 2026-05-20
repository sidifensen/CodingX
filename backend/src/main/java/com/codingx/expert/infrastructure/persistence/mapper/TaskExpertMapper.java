package com.codingx.expert.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.expert.infrastructure.persistence.dataobject.TaskExpertDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义任务专家绑定表 Mapper。
 */
@Mapper
public interface TaskExpertMapper extends BaseMapper<TaskExpertDO> {
}
