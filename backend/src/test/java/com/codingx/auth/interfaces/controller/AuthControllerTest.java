package com.codingx.auth.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.auth.application.command.LoginCommand;
import com.codingx.auth.application.service.AuthApplicationService;
import com.codingx.auth.application.service.LoginResult;
import com.codingx.auth.application.service.UserViewService;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.interfaces.request.LoginRequest;
import com.codingx.auth.interfaces.response.LoginResponse;
import com.codingx.auth.interfaces.response.MeResponse;
import com.codingx.common.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证认证控制器只做 HTTP 协议适配并委托应用服务。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthApplicationService authApplicationService;

    @Mock
    private UserViewService userViewService;

    @InjectMocks
    private AuthController authController;

    /**
     * 登录接口应把请求转换为 command，并由视图服务生成登录响应。
     */
    @Test
    void loginDelegatesToApplicationAndViewServices() {
        LoginResult loginResult = new LoginResult(1002L, "demo", "演示用户", UserType.USER, "token-1");
        LoginResponse loginResponse = new LoginResponse(1002L, "demo", "演示用户", UserType.USER, "token-1");
        when(authApplicationService.login(new LoginCommand("demo", "password"))).thenReturn(loginResult);
        when(userViewService.toLoginResponse(loginResult)).thenReturn(loginResponse);

        ApiResponse<LoginResponse> response = authController.login(new LoginRequest("demo", "password"));

        assertEquals(loginResponse, response.data());
        verify(userViewService).toLoginResponse(loginResult);
    }

    /**
     * 当前用户接口应只读取应用服务结果，响应字段投影交给视图服务。
     */
    @Test
    void meDelegatesToApplicationAndViewServices() {
        User user = User.create(1002L, "demo", "演示用户", "hash", UserType.USER, UserStatus.ACTIVE);
        MeResponse meResponse = new MeResponse(1002L, "demo", "演示用户", UserType.USER);
        when(authApplicationService.currentUser()).thenReturn(user);
        when(userViewService.toMeResponse(user)).thenReturn(meResponse);

        ApiResponse<MeResponse> response = authController.me();

        assertEquals(meResponse, response.data());
        verify(userViewService).toMeResponse(user);
    }
}
