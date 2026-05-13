package com.codingx.backend.auth.domain.service;

import com.codingx.backend.auth.domain.model.User;

public interface AuthSessionGateway {

    String login(User user);

    void logoutCurrent();

    Long currentLoginId();
}