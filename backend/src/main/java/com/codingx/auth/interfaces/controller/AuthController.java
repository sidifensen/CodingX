package com.codingx.auth.interfaces.controller;
import com.codingx.auth.application.command.LoginCommand;
import com.codingx.auth.application.service.AuthApplicationService;
import com.codingx.auth.application.service.LoginResult;
import com.codingx.auth.application.service.UserViewService;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.interfaces.request.LoginRequest;
import com.codingx.auth.interfaces.response.LoginResponse;
import com.codingx.auth.interfaces.response.MeResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 HTTP 控制器，只负责登录、登出和当前用户接口的协议适配。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * 认证应用服务，承接登录校验、登出和当前用户读取。
     */
    private final AuthApplicationService authApplicationService;

    /**
     * 用户视图服务，负责把应用层结果转换为接口响应对象。
     */
    private final UserViewService userViewService;

    /**
     * 校验账号密码并创建登录会话。
     * @param request 登录请求。
     * @return 登录响应，包含用户身份与访问令牌。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 步骤 1：将 HTTP 请求体转换为登录命令，交给应用服务完成凭证校验和会话创建。
        LoginResult result = authApplicationService.login(new LoginCommand(request.username(), request.password()));
        // 步骤 2：响应字段投影由视图服务统一处理，Controller 不重复拆解 LoginResult。
        return ApiResponse.success(userViewService.toLoginResponse(result));
    }

    /**
     * 清理当前登录会话。
     * @return 退出成功提示。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 步骤 1：应用服务通过会话网关清理当前登录态。
        authApplicationService.logoutCurrent();
        // 步骤 2：退出接口只返回统一中文成功提示，不携带业务数据。
        return ApiResponse.successMessage(ErrorMessageCatalog.AUTH_LOGOUT_SUCCESS);
    }

    /**
     * 查询当前登录用户的基础身份信息。
     * @return 当前用户响应。
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        // 步骤 1：应用服务负责读取当前登录用户并处理未登录或用户缺失异常。
        User user = authApplicationService.currentUser();
        // 步骤 2：视图服务只投影前端身份展示字段，避免返回敏感信息。
        return ApiResponse.success(userViewService.toMeResponse(user));
    }
}
