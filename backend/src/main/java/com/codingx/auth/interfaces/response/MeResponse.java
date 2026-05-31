package com.codingx.auth.interfaces.response;
import com.codingx.auth.domain.model.UserType;

/**
 * 当前登录用户响应体，只暴露前端身份展示和入口权限判断所需的信息。
 */
public record MeResponse(
    Long userId, // 当前登录用户主键。
    String username, // 登录用户名。
    String displayName, // 用户展示名称。
    UserType userType // 用户类型，用于前端判断可访问入口。
) {
}
