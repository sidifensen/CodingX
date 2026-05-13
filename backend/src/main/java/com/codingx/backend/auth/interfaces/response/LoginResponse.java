package com.codingx.backend.auth.interfaces.response;

import com.codingx.backend.auth.domain.model.UserType;

public record LoginResponse(Long userId, String username, String displayName, UserType userType, String token) {
}