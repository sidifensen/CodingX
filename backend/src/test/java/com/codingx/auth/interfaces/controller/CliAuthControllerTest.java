package com.codingx.auth.interfaces.controller;

import com.codingx.auth.application.service.CliAuthApplicationService;
import com.codingx.auth.application.service.CliAuthorizeRequest;
import com.codingx.auth.application.service.CliAuthorizeResponse;
import com.codingx.auth.application.service.CliDeviceAuthorizeRequest;
import com.codingx.auth.application.service.CliDeviceStartResponse;
import com.codingx.auth.application.service.CliDeviceTokenRequest;
import com.codingx.auth.application.service.CliDeviceTokenResponse;
import com.codingx.auth.application.service.CliTokenExchangeRequest;
import com.codingx.auth.application.service.LoginResult;
import com.codingx.auth.application.service.UserViewService;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.interfaces.response.LoginResponse;
import com.codingx.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 CLI 授权控制器的 HTTP 契约，确保 Controller 只做协议适配和响应封装。
 */
@ExtendWith(MockitoExtension.class)
class CliAuthControllerTest {

    /**
     * CLI 授权应用服务测试替身。
     */
    @Mock
    private CliAuthApplicationService cliAuthApplicationService;

    /**
     * 用户视图服务，用于把 LoginResult 投影成现有 LoginResponse。
     */
    @Mock
    private UserViewService userViewService;

    /**
     * 被测 CLI 授权控制器。
     */
    @InjectMocks
    private CliAuthController cliAuthController;

    /**
     * 浏览器授权接口应返回一次性授权码。
     */
    @Test
    void authorizeReturnsAuthorizationCode() throws Exception {
        when(cliAuthApplicationService.authorize(any(CliAuthorizeRequest.class)))
            .thenReturn(new CliAuthorizeResponse("code-1", "state-1", 120));

        mockMvc().perform(post("/api/auth/cli/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "state": "state-1",
                      "redirectUri": "http://127.0.0.1:49152/callback",
                      "codeChallenge": "challenge-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("code-1"))
            .andExpect(jsonPath("$.data.state").value("state-1"));

        verify(cliAuthApplicationService).authorize(any(CliAuthorizeRequest.class));
    }

    /**
     * CLI token 兑换接口应复用现有登录响应结构，减少前端和 CLI 的解析分叉。
     */
    @Test
    void tokenExchangeReturnsLoginResponse() throws Exception {
        LoginResult loginResult = new LoginResult(1002L, "demo", "演示用户", UserType.USER, "token-1");
        LoginResponse loginResponse = new LoginResponse(1002L, "demo", "演示用户", UserType.USER, "token-1");
        when(cliAuthApplicationService.exchangeToken(any(CliTokenExchangeRequest.class))).thenReturn(loginResult);
        when(userViewService.toLoginResponse(loginResult)).thenReturn(loginResponse);

        mockMvc().perform(post("/api/auth/cli/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "code-1",
                      "state": "state-1",
                      "codeVerifier": "verifier-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").value("token-1"));

        verify(userViewService).toLoginResponse(loginResult);
    }

    /**
     * 设备码启动接口应返回终端展示所需的验证码、验证地址和轮询间隔。
     */
    @Test
    void deviceStartReturnsPollingMetadata() throws Exception {
        when(cliAuthApplicationService.startDeviceAuthorization()).thenReturn(
            new CliDeviceStartResponse("device-1", "ABCD-EFGH", "http://localhost:5002/cli-login", 600, 2)
        );

        mockMvc().perform(post("/api/auth/cli/device/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userCode").value("ABCD-EFGH"))
            .andExpect(jsonPath("$.data.pollIntervalSeconds").value(2));
    }

    /**
     * 浏览器设备授权接口只返回成功语义，不泄露 CLI deviceCode。
     */
    @Test
    void deviceAuthorizeReturnsSuccessMessage() throws Exception {
        mockMvc().perform(post("/api/auth/cli/device/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userCode\":\"ABCD-EFGH\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(cliAuthApplicationService).authorizeDevice(any(CliDeviceAuthorizeRequest.class));
    }

    /**
     * 设备码 token 接口在 approved 时返回登录响应，在 pending 时返回状态供 CLI 继续轮询。
     */
    @Test
    void deviceTokenReturnsPendingStatus() throws Exception {
        when(cliAuthApplicationService.exchangeDeviceToken(any(CliDeviceTokenRequest.class)))
            .thenReturn(CliDeviceTokenResponse.pending("authorization_pending"));

        mockMvc().perform(post("/api/auth/cli/device/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"device-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.approved").value(false))
            .andExpect(jsonPath("$.data.status").value("authorization_pending"));
    }

    /**
     * 组装只包含当前 Controller 和全局异常处理器的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(cliAuthController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}

