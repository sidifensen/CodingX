package com.codingx.backend.auth.domain.repository;
import com.codingx.backend.auth.domain.model.User;
import java.util.Optional;

/**
 * Defines the repository contract exposed by UserRepository.
 */
public interface UserRepository {

    /**
     * Finds the data required by findById.
     * @param id input argument.
     * @return processing result.
     */
    Optional<User> findById(Long id);

    /**
     * Finds the data required by findByUsername.
     * @param username input argument.
     * @return processing result.
     */
    Optional<User> findByUsername(String username);

    /**
     * Persists the state handled by save.
     * @param user input argument.
     */
    void save(User user);
}
