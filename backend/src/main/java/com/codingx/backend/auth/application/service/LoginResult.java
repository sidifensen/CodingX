package com.codingx.backend.auth.application.service;

import com.codingx.backend.auth.domain.model.UserType;

public record LoginResult(Long userId, String username, String displayName, UserType userType, String token) {
}