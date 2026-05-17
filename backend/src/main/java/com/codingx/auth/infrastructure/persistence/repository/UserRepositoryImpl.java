package com.codingx.auth.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.auth.application.service.AdminUserPageView;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.infrastructure.persistence.dataobject.UserDO;
import com.codingx.auth.infrastructure.persistence.mapper.UserMapper;
import cn.hutool.core.util.StrUtil;
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
        return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getId, id)
                .eq(UserDO::getDeleted, 0)))
            .map(this::toDomain);
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
     * 按管理端筛选条件分页查询用户。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param status 可选状态过滤。
     * @param keyword 可选关键词（用户名/展示名/邮箱）。
     * @return 分页结果。
     */
    @Override
    public AdminUserPageView pageUsers(int current, int size, UserStatus status, String keyword) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getDeleted, 0)
            .eq(status != null, UserDO::getStatus, status == null ? null : status.name())
            .and(StrUtil.isNotBlank(keyword), query -> query
                .like(UserDO::getUsername, keyword)
                .or()
                .like(UserDO::getDisplayName, keyword)
                .or()
                .like(UserDO::getEmail, keyword))
            .orderByDesc(UserDO::getCreatedAt);

        Page<UserDO> page = userMapper.selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), wrapper);
        return AdminUserPageView.builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .current(page.getCurrent())
            .size(page.getSize())
            .pages(page.getPages())
            .build();
    }

    /**
     * 判断指定邮箱是否已被其他用户占用。
     * @param email 邮箱。
     * @param excludeUserId 排除用户 ID，可空。
     * @return true 表示邮箱已存在。
     */
    @Override
    public boolean existsByEmail(String email, Long excludeUserId) {
        if (StrUtil.isBlank(email)) {
            return false;
        }
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getDeleted, 0)
            .eq(UserDO::getEmail, email)
            .ne(excludeUserId != null, UserDO::getId, excludeUserId)
            .last("limit 1");
        return userMapper.selectOne(wrapper) != null;
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
        ).toBuilder()
            .email(dataObject.getEmail())
            .phone(dataObject.getPhone())
            .avatarUrl(dataObject.getAvatarUrl())
            .lastLoginAt(dataObject.getLastLoginAt())
            .lastLoginIp(dataObject.getLastLoginIp())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .build();
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
        dataObject.setEmail(user.getEmail());
        dataObject.setPhone(user.getPhone());
        dataObject.setAvatarUrl(user.getAvatarUrl());
        dataObject.setLastLoginAt(user.getLastLoginAt());
        dataObject.setLastLoginIp(user.getLastLoginIp());
        dataObject.setCreatedAt(user.getCreatedAt());
        dataObject.setUpdatedAt(user.getUpdatedAt());
        dataObject.setDeleted(0);
        return dataObject;
    }
}
