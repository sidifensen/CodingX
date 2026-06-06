package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernancePermissionAuditDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限审计表 Mapper，用于写入和查询工具执行判定记录。
 */
@Mapper
public interface GovernancePermissionAuditMapper extends BaseMapper<GovernancePermissionAuditDO> {
}
