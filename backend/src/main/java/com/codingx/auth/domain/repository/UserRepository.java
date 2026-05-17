package com.codingx.auth.domain.repository;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.application.service.AdminUserPageView;
import java.util.Optional;

/**
 * 定义 UserRepository 的仓储契约。
 */
public interface UserRepository {

    /**
     * 查询 findById 需要的数据。
     * @param id 输入参数。
     * @return 输入参数。
     */
    Optional<User> findById(Long id);

    /**
     * 查询 findByUsername 需要的数据。
     * @param username 输入参数。
     * @return 输入参数。
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
     * 持久化 save 处理的状态。
     * @param user 输入参数。
     */
    void save(User user);
}
