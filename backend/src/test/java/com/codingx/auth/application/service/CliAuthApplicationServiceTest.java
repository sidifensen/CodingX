package com.codingx.auth.application.service;

import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 CLI 浏览器登录授权的应用层规则，覆盖授权码、PKCE 和设备码主链路。
 */
class CliAuthApplicationServiceTest {

    /**
     * 测试用用户仓储，按固定用户编号返回活跃用户。
     */
    private FakeUserRepository userRepository;

    /**
     * 测试用会话网关，模拟当前浏览器登录用户和 CLI 换取的新 token。
     */
    private FakeAuthSessionGateway authSessionGateway;

    /**
     * CLI 授权状态存储，测试直接使用生产内存实现以覆盖一次性消费语义。
     */
    private InMemoryCliAuthStateStore stateStore;

    /**
     * 被测 CLI 授权应用服务。
     */
    private CliAuthApplicationService service;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        authSessionGateway = new FakeAuthSessionGateway();
        stateStore = new InMemoryCliAuthStateStore();
        service = new CliAuthApplicationService(userRepository, authSessionGateway, stateStore);
    }

    /**
     * loopback 授权码只接受本机回调地址，避免浏览器把 code 跳转给第三方站点。
     */
    @Test
    void authorizeRejectsNonLoopbackRedirectUri() {
        CliAuthorizeRequest request = new CliAuthorizeRequest(
            "state-1",
            "https://evil.example/callback",
            codeChallenge("verifier-1")
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.authorize(request));

        assertEquals("CLI_AUTH_REDIRECT_INVALID", exception.getCode());
        assertTrue(exception.getMessage().contains("本机回调地址"));
    }

    /**
     * 授权码兑换时必须校验 state 和 PKCE verifier，并且返回为 CLI 新建的登录 token。
     */
    @Test
    void exchangeTokenReturnsLoginResultWhenStateAndVerifierMatch() {
        String verifier = "codingx-cli-verifier";
        CliAuthorizeResponse authorizeResponse = service.authorize(new CliAuthorizeRequest(
            "state-1",
            "http://127.0.0.1:49152/callback",
            codeChallenge(verifier)
        ));

        LoginResult result = service.exchangeToken(new CliTokenExchangeRequest(
            authorizeResponse.code(),
            "state-1",
            verifier
        ));

        assertEquals(1002L, result.userId());
        assertEquals("demo", result.username());
        assertEquals("cli-token-1002", result.token());
    }

    /**
     * 授权码只能消费一次，防止浏览器历史或本机日志里的 code 被重复兑换。
     */
    @Test
    void exchangeTokenConsumesAuthorizationCodeOnlyOnce() {
        String verifier = "one-time-verifier";
        CliAuthorizeResponse authorizeResponse = service.authorize(new CliAuthorizeRequest(
            "state-1",
            "http://localhost:49152/callback",
            codeChallenge(verifier)
        ));
        service.exchangeToken(new CliTokenExchangeRequest(authorizeResponse.code(), "state-1", verifier));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service.exchangeToken(new CliTokenExchangeRequest(authorizeResponse.code(), "state-1", verifier)));

        assertEquals("CLI_AUTH_CODE_INVALID", exception.getCode());
        assertTrue(exception.getMessage().contains("授权码"));
    }

    /**
     * verifier 不匹配时不应发放 token，并且授权码仍被消费以降低暴力尝试价值。
     */
    @Test
    void exchangeTokenRejectsMismatchedVerifier() {
        CliAuthorizeResponse authorizeResponse = service.authorize(new CliAuthorizeRequest(
            "state-1",
            "http://127.0.0.1:49152/callback",
            codeChallenge("expected-verifier")
        ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service.exchangeToken(new CliTokenExchangeRequest(authorizeResponse.code(), "state-1", "wrong-verifier")));

        assertEquals("CLI_AUTH_VERIFIER_INVALID", exception.getCode());
        assertFalse(authSessionGateway.loginCalled);
    }

    /**
     * 设备码创建后，在浏览器授权前 CLI 轮询应保持 pending，不应误判为失败。
     */
    @Test
    void deviceTokenReportsPendingBeforeBrowserAuthorization() {
        CliDeviceStartResponse startResponse = service.startDeviceAuthorization();

        CliDeviceTokenResponse tokenResponse = service.exchangeDeviceToken(new CliDeviceTokenRequest(startResponse.deviceCode()));

        assertFalse(tokenResponse.approved());
        assertEquals("authorization_pending", tokenResponse.status());
    }

    /**
     * 浏览器输入 userCode 授权后，CLI 使用 deviceCode 轮询应拿到登录结果并消费会话。
     */
    @Test
    void deviceTokenReturnsLoginResultAfterBrowserAuthorization() {
        CliDeviceStartResponse startResponse = service.startDeviceAuthorization();

        service.authorizeDevice(new CliDeviceAuthorizeRequest(startResponse.userCode()));
        CliDeviceTokenResponse tokenResponse = service.exchangeDeviceToken(new CliDeviceTokenRequest(startResponse.deviceCode()));

        assertTrue(tokenResponse.approved());
        assertEquals("approved", tokenResponse.status());
        assertEquals("cli-token-1002", tokenResponse.login().token());

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service.exchangeDeviceToken(new CliDeviceTokenRequest(startResponse.deviceCode())));
        assertEquals("CLI_DEVICE_CODE_INVALID", exception.getCode());
    }

    /**
     * 使用和生产一致的 S256 规则计算测试 challenge，保证测试校验的是协议而不是硬编码值。
     */
    private String codeChallenge(String verifier) {
        return CliAuthApplicationService.toCodeChallenge(verifier);
    }

    /**
     * 固定用户仓储，避免测试依赖数据库。
     */
    private static class FakeUserRepository implements UserRepository {

        /**
         * 活跃用户，代表当前浏览器登录用户和 CLI 换 token 的目标账号。
         */
        private final User user = User.create(1002L, "demo", "演示用户", "hash", UserType.USER, UserStatus.ACTIVE);

        @Override
        public Optional<User> findById(Long id) {
            return Long.valueOf(1002L).equals(id) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public AdminUserPageView pageUsers(int current, int size, UserStatus status, String keyword) {
            throw new UnsupportedOperationException("CLI 授权测试不需要分页查询");
        }

        @Override
        public boolean existsByEmail(String email, Long excludeUserId) {
            return false;
        }

        @Override
        public void save(User user) {
            // CLI 授权不更新用户资料。
        }
    }

    /**
     * 固定登录上下文，模拟浏览器端当前已登录用户。
     */
    private static class FakeAuthSessionGateway implements AuthSessionGateway {

        /**
         * 记录是否创建过 CLI 新登录态，便于 verifier 失败场景断言。
         */
        private boolean loginCalled;

        @Override
        public String login(User user) {
            loginCalled = true;
            return "cli-token-" + user.getId();
        }

        @Override
        public void logoutCurrent() {
            // 测试不覆盖退出登录。
        }

        @Override
        public Long currentLoginId() {
            return 1002L;
        }

        @Override
        public String currentRequestIp() {
            return "127.0.0.1";
        }
    }
}

