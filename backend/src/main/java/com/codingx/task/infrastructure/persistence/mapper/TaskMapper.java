package com.codingx.task.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.task.infrastructure.persistence.dataobject.TaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台任务表 Mapper，继承 MyBatis-Plus 通用 CRUD 能力，供任务命令与查询服务持久化 task 表。
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {
}
