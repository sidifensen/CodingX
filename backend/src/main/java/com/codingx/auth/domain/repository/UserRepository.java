package com.codingx.auth.domain.repository;
import com.codingx.auth.domain.model.User;
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
     * 持久化 save 处理的状态。
     * @param user 输入参数。
     */
    void save(User user);
}
