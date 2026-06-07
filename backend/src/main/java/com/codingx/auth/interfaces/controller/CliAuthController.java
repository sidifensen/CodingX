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
import com.codingx.auth.interfaces.response.LoginResponse;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CLI 授权 HTTP 控制器，只负责浏览器授权、设备码授权和 token 兑换的协议适配。
 */
@RestController
@RequestMapping("/api/auth/cli")
@RequiredArgsConstructor
public class CliAuthController {

    /**
     * CLI 授权应用服务，承接授权码、设备码和登录 token 创建规则。
     */
    private final CliAuthApplicationService cliAuthApplicationService;

    /**
     * 用户视图服务，用于复用现有登录响应投影。
     */
    private final UserViewService userViewService;

    /**
     * 浏览器登录后为 CLI 创建一次性授权码。
     *
     * @param request 授权请求。
     * @return 授权码响应。
     */
    @PostMapping("/authorize")
    public ApiResponse<CliAuthorizeResponse> authorize(@RequestBody CliAuthorizeRequest request) {
        // 步骤 1：Controller 不解析认证状态，应用服务通过会话网关读取当前登录用户。
        // 步骤 2：授权码响应只包含 code/state/过期秒数，不返回真实 token。
        return ApiResponse.success(cliAuthApplicationService.authorize(request));
    }

    /**
     * CLI 使用授权码换取登录响应。
     *
     * @param request token 兑换请求。
     * @return 登录响应。
     */
    @PostMapping("/token")
    public ApiResponse<LoginResponse> token(@RequestBody CliTokenExchangeRequest request) {
        // 步骤 1：应用服务完成一次性 code 消费、state 校验和 PKCE 校验。
        LoginResult loginResult = cliAuthApplicationService.exchangeToken(request);
        // 步骤 2：复用现有 LoginResponse，避免 CLI 和前端解析两套用户结构。
        return ApiResponse.success(userViewService.toLoginResponse(loginResult));
    }

    /**
     * CLI 启动设备码登录。
     *
     * @return 设备码元数据。
     */
    @PostMapping("/device/start")
    public ApiResponse<CliDeviceStartResponse> startDevice() {
        // 步骤：创建 pending 设备授权会话，返回终端展示和轮询所需字段。
        return ApiResponse.success(cliAuthApplicationService.startDeviceAuthorization());
    }

    /**
     * 浏览器确认设备码授权。
     *
     * @param request 设备码授权请求。
     * @return 成功响应。
     */
    @PostMapping("/device/authorize")
    public ApiResponse<Void> authorizeDevice(@RequestBody CliDeviceAuthorizeRequest request) {
        // 步骤：应用服务将当前浏览器登录用户绑定到 userCode 对应设备会话。
        cliAuthApplicationService.authorizeDevice(request);
        return ApiResponse.successMessage("CLI 授权完成，请回到终端继续使用");
    }

    /**
     * CLI 轮询设备码登录结果。
     *
     * @param request 设备码 token 轮询请求。
     * @return pending 或 approved 响应。
     */
    @PostMapping("/device/token")
    public ApiResponse<CliDeviceTokenResponse> deviceToken(@RequestBody CliDeviceTokenRequest request) {
        // 步骤：pending 时返回状态供 CLI 继续轮询，approved 时携带 LoginResult。
        return ApiResponse.success(cliAuthApplicationService.exchangeDeviceToken(request));
    }
}

