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
 * 用户仓储 MyBatis-Plus 实现，负责用户表与领域对象之间的转换。
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /**
     * 用户表 Mapper，用于执行用户查询、分页、新增和更新。
     */
    private final UserMapper userMapper;

    /**
     * 按主键查询未逻辑删除用户。
     * @param id 用户主键。
     * @return 用户领域对象，未找到时为空。
     */
    @Override
    public Optional<User> findById(Long id) {
        // 步骤 1：按主键和逻辑删除标记过滤，避免读到已删除账号。
        // 步骤 2：查询结果统一转换为用户领域对象。
        return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getId, id)
                .eq(UserDO::getDeleted, 0)))
            .map(this::toDomain);
    }

    /**
     * 按登录用户名查询未逻辑删除用户。
     * @param username 登录用户名。
     * @return 用户领域对象，未找到时为空。
     */
    @Override
    public Optional<User> findByUsername(String username) {
        // 步骤 1：登录名全局唯一，limit 1 避免异常脏数据影响登录链路。
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUsername, username)
            .eq(UserDO::getDeleted, 0)
            .last("limit 1");
        // 步骤 2：持久化对象只在仓储内部存在，对外返回领域对象。
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
        // 步骤 1：基础条件排除逻辑删除用户，并按可选状态过滤。
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getDeleted, 0)
            .eq(status != null, UserDO::getStatus, status == null ? null : status.name())
            // 步骤 2：关键词非空时同时匹配登录名、展示名和邮箱。
            .and(StrUtil.isNotBlank(keyword), query -> query
                .like(UserDO::getUsername, keyword)
                .or()
                .like(UserDO::getDisplayName, keyword)
                .or()
                .like(UserDO::getEmail, keyword))
            .orderByDesc(UserDO::getCreatedAt);

        // 步骤 3：页码和页大小兜底为至少 1，防止前端传入非法分页参数。
        Page<UserDO> page = userMapper.selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), wrapper);
        // 步骤 4：分页记录转换为领域对象，分页元数据按 MyBatis-Plus 结果透传。
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
        // 步骤 1：空邮箱不参与唯一性校验，允许用户不填写邮箱。
        if (StrUtil.isBlank(email)) {
            return false;
        }
        // 步骤 2：编辑用户时排除当前用户主键，只检查其他未删除账号。
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getDeleted, 0)
            .eq(UserDO::getEmail, email)
            .ne(excludeUserId != null, UserDO::getId, excludeUserId)
            .last("limit 1");
        // 步骤 3：只关心是否存在记录，不需要返回具体用户数据。
        return userMapper.selectOne(wrapper) != null;
    }

    /**
     * 保存用户聚合。
     * @param user 用户领域对象。
     */
    @Override
    public void save(User user) {
        // 步骤 1：先把领域对象转换为数据库行对象，隐藏表字段细节。
        UserDO dataObject = toDataObject(user);
        // 步骤 2：按主键是否存在决定新增或更新，保持仓储契约简单。
        if (userMapper.selectById(user.getId()) == null) {
            userMapper.insert(dataObject);
        } else {
            userMapper.updateById(dataObject);
        }
    }

    /**
     * 将数据库行对象转换为用户领域对象。
     * @param dataObject 用户表行对象。
     * @return 用户领域对象。
     */
    private User toDomain(UserDO dataObject) {
        // 步骤 1：核心认证字段通过领域工厂校验，避免非法数据绕过领域约束。
        // 步骤 2：资料和审计字段通过 builder 补齐，保持数据库映射完整。
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
     * 将用户领域对象转换为数据库行对象。
     * @param user 用户领域对象。
     * @return 用户表行对象。
     */
    private UserDO toDataObject(User user) {
        // 步骤 1：枚举字段以名称写入数据库，保持与历史表结构兼容。
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
        // 步骤 2：当前仓储只负责保存有效用户，默认写入未删除标记。
        dataObject.setDeleted(0);
        return dataObject;
    }
}
