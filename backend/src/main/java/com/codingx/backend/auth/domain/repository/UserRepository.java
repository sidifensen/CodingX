package com.codingx.backend.auth.domain.repository;

import com.codingx.backend.auth.domain.model.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    void save(User user);
}