package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceLongTermMemoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 长期记忆 Mapper，用于保存和读取用户级、项目级记忆。
 */
@Mapper
public interface GovernanceLongTermMemoryMapper extends BaseMapper<GovernanceLongTermMemoryDO> {
}
