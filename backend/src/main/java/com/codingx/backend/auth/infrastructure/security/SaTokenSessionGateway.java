package com.codingx.backend.auth.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.backend.auth.domain.model.User;
import com.codingx.backend.auth.domain.service.AuthSessionGateway;
import com.codingx.backend.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class SaTokenSessionGateway implements AuthSessionGateway {

    @Override
    public String login(User user) {
        StpUtil.login(user.getId());
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("displayName", user.getDisplayName());
        StpUtil.getSession().set("userType", user.getUserType().name());
        return StpUtil.getTokenValue();
    }

    @Override
    public void logoutCurrent() {
        StpUtil.logout();
    }

    @Override
    public Long currentLoginId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception exception) {
            throw new UnauthorizedException("Not logged in");
        }
    }
}