package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceProjectProfileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目画像表 Mapper，用于保存和读取工作空间扫描结果。
 */
@Mapper
public interface GovernanceProjectProfileMapper extends BaseMapper<GovernanceProjectProfileDO> {
}
