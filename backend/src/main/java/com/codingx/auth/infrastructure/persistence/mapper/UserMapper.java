package com.codingx.auth.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.auth.infrastructure.persistence.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义 UserMapper 的 Mapper 操作。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
