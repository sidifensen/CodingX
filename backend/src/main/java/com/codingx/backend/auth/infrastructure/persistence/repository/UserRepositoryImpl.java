package com.codingx.backend.auth.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.backend.auth.domain.model.User;
import com.codingx.backend.auth.domain.model.UserStatus;
import com.codingx.backend.auth.domain.model.UserType;
import com.codingx.backend.auth.domain.repository.UserRepository;
import com.codingx.backend.auth.infrastructure.persistence.dataobject.UserDO;
import com.codingx.backend.auth.infrastructure.persistence.mapper.UserMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the persistence behavior required by UserRepositoryImpl.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /**
     * userMapper value.
     */
    private final UserMapper userMapper;

    /**
     * Finds the data required by findById.
     * @param id input argument.
     * @return processing result.
     */
    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    /**
     * Finds the data required by findByUsername.
     * @param username input argument.
     * @return processing result.
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
     * Persists the state handled by save.
     * @param user input argument.
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
     * Executes the logic defined by toDomain.
     * @param dataObject input argument.
     * @return processing result.
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
     * Executes the logic defined by toDataObject.
     * @param user input argument.
     * @return processing result.
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
