package com.codingx.backend.auth.infrastructure.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.backend.auth.infrastructure.persistence.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Declares mapper operations used by UserMapper.
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
