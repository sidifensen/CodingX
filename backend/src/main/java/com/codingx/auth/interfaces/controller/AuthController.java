package com.codingx.auth.interfaces.controller;
import com.codingx.auth.application.command.LoginCommand;
import com.codingx.auth.application.service.AuthApplicationService;
import com.codingx.auth.application.service.LoginResult;
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
 * 负责处理 AuthController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * AuthApplicationService 依赖。
     */
    private final AuthApplicationService authApplicationService;

    /**
     * 校验当前用户并返回登录结果。
     * @param request 输入参数。
     * @return 输入参数。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authApplicationService.login(new LoginCommand(request.username(), request.password()));
        return ApiResponse.success(new LoginResponse(
            result.userId(),
            result.username(),
            result.displayName(),
            result.userType(),
            result.token()
        ));
    }

    /**
     * 清理当前登录会话。
     * @return 输入参数。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authApplicationService.logoutCurrent();
        return ApiResponse.successMessage(ErrorMessageCatalog.AUTH_LOGOUT_SUCCESS);
    }

    /**
     * 执行 me 定义的处理逻辑。
     * @return 输入参数。
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        User user = authApplicationService.currentUser();
        return ApiResponse.success(new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType()));
    }
}
