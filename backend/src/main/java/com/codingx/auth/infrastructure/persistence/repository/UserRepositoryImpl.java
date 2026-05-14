package com.codingx.auth.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.infrastructure.persistence.dataobject.UserDO;
import com.codingx.auth.infrastructure.persistence.mapper.UserMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 UserRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /**
     * UserMapper 依赖。
     */
    private final UserMapper userMapper;

    /**
     * 查询 findById 需要的数据。
     * @param id 输入参数。
     * @return 输入参数。
     */
    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 查询 findByUsername 需要的数据。
     * @param username 输入参数。
     * @return 输入参数。
     */
    @Override
    public Optional<User> findByUsername(String username) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUsername, username)
            .eq(UserDO::getDeleted, 0)
            .last("limit 1");
        return Optional.ofNullable(userMapper.selectOne(wrapper)).map(this::toDomain);
    }

    /**
     * 持久化 save 处理的状态。
     * @param user 输入参数。
     */
    @Override
    public void save(User user) {
        UserDO dataObject = toDataObject(user);
        if (userMapper.selectById(user.getId()) == null) {
            userMapper.insert(dataObject);
        } else {
            userMapper.updateById(dataObject);
        }
    }

    /**
     * 执行 toDomain 定义的处理逻辑。
     * @param dataObject 输入参数。
     * @return 输入参数。
     */
    private User toDomain(UserDO dataObject) {
        return User.create(
            dataObject.getId(),
            dataObject.getUsername(),
            dataObject.getDisplayName(),
            dataObject.getPasswordHash(),
            UserType.valueOf(dataObject.getUserType()),
            UserStatus.valueOf(dataObject.getStatus())
        );
    }

    /**
     * 执行 toDataObject 定义的处理逻辑。
     * @param user 输入参数。
     * @return 输入参数。
     */
    private UserDO toDataObject(User user) {
        UserDO dataObject = new UserDO();
        dataObject.setId(user.getId());
        dataObject.setUsername(user.getUsername());
        dataObject.setDisplayName(user.getDisplayName());
        dataObject.setPasswordHash(user.getPasswordHash());
        dataObject.setUserType(user.getUserType().name());
        dataObject.setStatus(user.getStatus().name());
        dataObject.setDeleted(0);
        return dataObject;
    }
}
