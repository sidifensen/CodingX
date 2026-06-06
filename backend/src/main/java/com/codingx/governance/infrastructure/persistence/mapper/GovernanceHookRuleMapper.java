package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceHookRuleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Hook 规则表 Mapper，用于执行生命周期规则的基础读写。
 */
@Mapper
public interface GovernanceHookRuleMapper extends BaseMapper<GovernanceHookRuleDO> {
}
