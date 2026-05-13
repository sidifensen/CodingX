package com.codingx.backend.auth.infrastructure.security;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.backend.auth.domain.model.User;
import com.codingx.backend.auth.domain.service.AuthSessionGateway;
import com.codingx.backend.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

/**
 * Bridges security infrastructure for SaTokenSessionGateway.
 */
@Component
public class SaTokenSessionGateway implements AuthSessionGateway {

    /**
     * Authenticates the current user and returns the login result.
     * @param user input argument.
     * @return processing result.
     */
    @Override
    public String login(User user) {
        StpUtil.login(user.getId());
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("displayName", user.getDisplayName());
        StpUtil.getSession().set("userType", user.getUserType().name());
        return StpUtil.getTokenValue();
    }

    /**
     * Clears the current login session.
     */
    @Override
    public void logoutCurrent() {
        StpUtil.logout();
    }

    /**
     * Returns the data for the current authenticated user.
     * @return processing result.
     */
    @Override
    public Long currentLoginId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception exception) {
            throw new UnauthorizedException("Not logged in");
        }
    }
}
