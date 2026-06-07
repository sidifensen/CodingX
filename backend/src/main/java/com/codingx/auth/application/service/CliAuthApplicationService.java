package com.codingx.auth.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CLI 授权应用服务，负责浏览器 loopback 登录和设备码登录的短期授权状态流转。
 */
@Service
@RequiredArgsConstructor
public class CliAuthApplicationService {

    /**
     * 授权码有效期，过短会影响用户浏览器跳转，过长会扩大 code 泄露窗口。
     */
    private static final Duration AUTHORIZATION_TTL = Duration.ofSeconds(120);

    /**
     * 设备码有效期，允许远程终端用户有足够时间打开网页输入验证码。
     */
    private static final Duration DEVICE_TTL = Duration.ofMinutes(10);

    /**
     * CLI 推荐轮询间隔，避免设备码 pending 时给后端造成压力。
     */
    private static final long DEVICE_POLL_INTERVAL_SECONDS = 2;

    /**
     * 用户仓储，用于把授权绑定的 userId 重新加载为登录用户。
     */
    private final UserRepository userRepository;

    /**
     * 认证会话网关，用于读取浏览器当前登录用户和为 CLI 创建新 token。
     */
    private final AuthSessionGateway authSessionGateway;

    /**
     * CLI 短期授权状态存储，当前为内存实现，后续可替换 Redis。
     */
    private final CliAuthStateStore stateStore;

    /**
     * 浏览器已登录后创建 CLI 一次性授权码。
     *
     * @param request 授权请求。
     * @return 授权码响应。
     */
    public CliAuthorizeResponse authorize(CliAuthorizeRequest request) {
        // 步骤 1：校验浏览器传入的 loopback 回调和 PKCE 字段，阻止授权码跳转到第三方站点。
        validateAuthorizeRequest(request);
        // 步骤 2：读取当前浏览器登录用户，授权码只绑定用户 ID，不持久化真实 token。
        Long userId = authSessionGateway.currentLoginId();
        String code = "cli_code_" + IdUtil.fastSimpleUUID();
        Instant expiresAt = Instant.now().plus(AUTHORIZATION_TTL);
        // 步骤 3：保存短期授权快照，等待 CLI 使用 code + verifier 兑换。
        stateStore.saveAuthorization(new CliAuthStateStore.CliAuthorization(
            code,
            userId,
            request.state().trim(),
            request.redirectUri().trim(),
            request.codeChallenge().trim(),
            expiresAt
        ));
        return new CliAuthorizeResponse(code, request.state().trim(), AUTHORIZATION_TTL.toSeconds());
    }

    /**
     * CLI 使用授权码兑换专属登录 token。
     *
     * @param request token 兑换请求。
     * @return 登录结果。
     */
    public LoginResult exchangeToken(CliTokenExchangeRequest request) {
        // 步骤 1：授权码读取即消费，避免 verifier 失败后的暴力重试和重复兑换。
        CliAuthStateStore.CliAuthorization authorization = stateStore.consumeAuthorization(request.code())
            .orElseThrow(() -> new BusinessException("CLI_AUTH_CODE_INVALID", "CLI 授权码不存在或已失效"));
        // 步骤 2：state 必须匹配 CLI 原始请求，防止不同登录请求串用回调结果。
        if (!StrUtil.equals(authorization.state(), StrUtil.trimToEmpty(request.state()))) {
            throw new BusinessException("CLI_AUTH_STATE_INVALID", "CLI 登录状态校验失败，请重新登录");
        }
        // 步骤 3：按 S256 规则校验 verifier，确认兑换方就是发起登录的 CLI。
        if (!StrUtil.equals(authorization.codeChallenge(), toCodeChallenge(request.codeVerifier()))) {
            throw new BusinessException("CLI_AUTH_VERIFIER_INVALID", "CLI 登录校验失败，请重新登录");
        }
        // 步骤 4：重新加载用户最新状态并创建 CLI 专用登录 token。
        return loginForUser(authorization.userId());
    }

    /**
     * 启动设备码登录会话。
     *
     * @return 设备码元数据。
     */
    public CliDeviceStartResponse startDeviceAuthorization() {
        // 步骤 1：生成内部 deviceCode 和用户可读 userCode，两者分离避免网页暴露轮询凭据。
        String deviceCode = "cli_device_" + IdUtil.fastSimpleUUID();
        String userCode = randomUserCode();
        Instant expiresAt = Instant.now().plus(DEVICE_TTL);
        // 步骤 2：保存 pending 设备授权，等待浏览器登录后绑定 userId。
        stateStore.saveDeviceAuthorization(new CliAuthStateStore.CliDeviceAuthorization(
            deviceCode,
            userCode,
            null,
            expiresAt
        ));
        return new CliDeviceStartResponse(
            deviceCode,
            userCode,
            "/cli-login",
            DEVICE_TTL.toSeconds(),
            DEVICE_POLL_INTERVAL_SECONDS
        );
    }

    /**
     * 浏览器确认设备码授权。
     *
     * @param request 设备码授权请求。
     */
    public void authorizeDevice(CliDeviceAuthorizeRequest request) {
        // 步骤 1：根据用户可读 code 找到 pending 会话，过期或不存在时返回中文业务错误。
        CliAuthStateStore.CliDeviceAuthorization deviceAuthorization = stateStore.findDeviceByUserCode(request.userCode())
            .orElseThrow(() -> new BusinessException("CLI_DEVICE_CODE_INVALID", "CLI 设备验证码不存在或已失效"));
        // 步骤 2：绑定当前浏览器登录用户，CLI 下一轮轮询即可换取 token。
        stateStore.updateDeviceAuthorization(deviceAuthorization.approve(authSessionGateway.currentLoginId()));
    }

    /**
     * CLI 轮询设备码 token。
     *
     * @param request 轮询请求。
     * @return pending 或 approved 响应。
     */
    public CliDeviceTokenResponse exchangeDeviceToken(CliDeviceTokenRequest request) {
        // 步骤 1：读取设备状态，不存在时说明过期、已消费或 deviceCode 非法。
        CliAuthStateStore.CliDeviceAuthorization deviceAuthorization = stateStore.findDeviceByDeviceCode(request.deviceCode())
            .orElseThrow(() -> new BusinessException("CLI_DEVICE_CODE_INVALID", "CLI 设备验证码不存在或已失效"));
        // 步骤 2：未绑定用户时返回 pending，CLI 应继续按 interval 轮询。
        if (deviceAuthorization.userId() == null) {
            return CliDeviceTokenResponse.pending("authorization_pending");
        }
        // 步骤 3：approved 状态读取后立即消费，避免同一个设备码重复换 token。
        CliAuthStateStore.CliDeviceAuthorization consumed = stateStore.consumeDeviceAuthorization(request.deviceCode())
            .orElseThrow(() -> new BusinessException("CLI_DEVICE_CODE_INVALID", "CLI 设备验证码不存在或已失效"));
        return CliDeviceTokenResponse.approved(loginForUser(consumed.userId()));
    }

    /**
     * 根据 PKCE S256 规则计算 code challenge。
     *
     * @param codeVerifier CLI 原始 verifier。
     * @return base64url 编码后的 SHA-256 摘要。
     */
    public static String toCodeChallenge(String codeVerifier) {
        byte[] digest = DigestUtil.sha256(StrUtil.trimToEmpty(codeVerifier).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * 校验浏览器授权请求参数。
     *
     * @param request 授权请求。
     */
    private void validateAuthorizeRequest(CliAuthorizeRequest request) {
        if (request == null || StrUtil.hasBlank(request.state(), request.redirectUri(), request.codeChallenge())) {
            throw new BusinessException("CLI_AUTH_REQUEST_INVALID", "CLI 登录授权参数不能为空");
        }
        if (!isLoopbackCallback(request.redirectUri())) {
            throw new BusinessException("CLI_AUTH_REDIRECT_INVALID", "CLI 登录只允许使用本机回调地址");
        }
    }

    /**
     * 判断 redirectUri 是否为 CLI 本机 loopback callback。
     *
     * @param redirectUri 原始回调地址。
     * @return true 表示允许跳转。
     */
    private boolean isLoopbackCallback(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri.trim());
            String host = StrUtil.trimToEmpty(uri.getHost());
            return "http".equalsIgnoreCase(uri.getScheme())
                && uri.getPort() > 0
                && "/callback".equals(uri.getPath())
                && StrUtil.equalsAnyIgnoreCase(host, "127.0.0.1", "localhost", "::1");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 为指定用户创建 CLI 登录结果。
     *
     * @param userId 用户主键。
     * @return 登录结果。
     */
    private LoginResult loginForUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("当前登录用户不存在"));
        user.ensureActive();
        String token = authSessionGateway.login(user);
        return new LoginResult(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType(), token);
    }

    /**
     * 生成便于用户输入的设备验证码。
     *
     * @return 形如 ABCD-EFGH 的验证码。
     */
    private String randomUserCode() {
        return RandomUtil.randomStringUpper(4) + "-" + RandomUtil.randomStringUpper(4);
    }
}

