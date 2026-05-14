package com.codingx.auth.infrastructure.security;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

/**
 * 实现 SaTokenSessionGateway 的安全基础设施适配。
 */
@Component
public class SaTokenSessionGateway implements AuthSessionGateway {

    /**
     * 校验当前用户并返回登录结果。
     * @param user 输入参数。
     * @return 输入参数。
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
     * 清理当前登录会话。
     */
    @Override
    public void logoutCurrent() {
        StpUtil.logout();
    }

    /**
     * 返回当前登录用户信息。
     * @return 输入参数。
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
