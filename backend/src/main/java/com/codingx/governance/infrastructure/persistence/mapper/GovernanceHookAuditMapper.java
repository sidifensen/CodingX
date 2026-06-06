package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceHookAuditDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Hook 审计表 Mapper，用于保存生命周期触发记录。
 */
@Mapper
public interface GovernanceHookAuditMapper extends BaseMapper<GovernanceHookAuditDO> {
}
