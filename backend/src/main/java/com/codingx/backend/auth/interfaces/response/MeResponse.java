package com.codingx.backend.auth.interfaces.response;

import com.codingx.backend.auth.domain.model.UserType;

public record MeResponse(Long userId, String username, String displayName, UserType userType) {
}