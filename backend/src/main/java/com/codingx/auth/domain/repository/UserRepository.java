package com.codingx.auth.domain.repository;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.application.service.AdminUserPageView;
import java.util.Optional;

/**
 * 用户仓储契约，隔离认证领域对象与具体持久化实现。
 */
public interface UserRepository {

    /**
     * 按主键查询未逻辑删除的用户。
     * @param id 用户主键。
     * @return 用户领域对象，用户不存在时为空。
     */
    Optional<User> findById(Long id);

    /**
     * 按登录用户名查询未逻辑删除的用户。
     * @param username 登录用户名。
     * @return 用户领域对象，用户不存在时为空。
     */
    Optional<User> findByUsername(String username);

    /**
     * 按管理端筛选条件分页查询用户。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param status 可选状态过滤。
     * @param keyword 可选关键词（用户名/展示名/邮箱）。
     * @return 分页结果。
     */
    AdminUserPageView pageUsers(int current, int size, UserStatus status, String keyword);

    /**
     * 判断指定邮箱是否已被其他用户占用。
     * @param email 邮箱。
     * @param excludeUserId 排除用户 ID，可空。
     * @return true 表示邮箱已存在。
     */
    boolean existsByEmail(String email, Long excludeUserId);

    /**
     * 保存用户聚合，新增和更新由基础设施层根据主键存在性判断。
     * @param user 用户领域对象。
     */
    void save(User user);
}
