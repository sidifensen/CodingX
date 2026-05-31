package com.codingx.auth.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.auth.infrastructure.persistence.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper，继承 MyBatis-Plus 通用 CRUD 能力，供认证与管理端用户服务读写 user 表。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
