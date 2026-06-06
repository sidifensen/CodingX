package com.codingx.governance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceSlashCommandDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Slash Command 表 Mapper，用于读取和维护用户端命令目录。
 */
@Mapper
public interface GovernanceSlashCommandMapper extends BaseMapper<GovernanceSlashCommandDO> {
}
