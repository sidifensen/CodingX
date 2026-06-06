package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernancePermissionPolicyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限策略表 Mapper，用于执行策略配置的基础读写。
 */
@Mapper
public interface GovernancePermissionPolicyMapper extends BaseMapper<GovernancePermissionPolicyDO> {
}
