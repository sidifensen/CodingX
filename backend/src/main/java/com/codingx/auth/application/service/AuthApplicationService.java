package com.codingx.auth.application.service;
import cn.hutool.core.util.StrUtil;
import com.codingx.auth.application.command.LoginCommand;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.auth.domain.service.PasswordHasher;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务，负责登录、登出和当前用户读取等认证用例。
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    /**
     * 用户仓储，用于读取账号、更新登录审计信息和查询当前用户。
     */
    private final UserRepository userRepository;

    /**
     * 密码哈希服务，用于明文密码校验和安全隔离。
     */
    private final PasswordHasher passwordHasher;

    /**
     * 会话网关，用于对接 Sa-Token 登录态和当前请求上下文。
     */
    private final AuthSessionGateway authSessionGateway;

    /**
     * 校验账号密码并创建登录会话。
     * @param command 登录命令。
     * @return 登录成功后的用户身份与访问令牌。
     */
    public LoginResult login(LoginCommand command) {
        // 步骤 1：登录名和密码都不能为空，避免空输入落到仓储或密码哈希组件。
        if (StrUtil.hasBlank(command.username(), command.password())) {
            throw new IllegalArgumentException(ErrorMessageCatalog.LOGIN_REQUIRED_CREDENTIALS);
        }
        // 步骤 2：按用户名读取账号，不存在时统一返回账号或密码错误，避免暴露账号枚举信息。
        User user = userRepository.findByUsername(command.username())
            .orElseThrow(() -> new UnauthorizedException(ErrorMessageCatalog.LOGIN_INVALID_CREDENTIALS));
        // 步骤 3：账号必须处于 ACTIVE 状态，禁用或待审核账号不能创建会话。
        user.ensureActive();
        // 步骤 4：使用领域密码服务校验明文密码和哈希值，失败时仍返回统一凭证错误。
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorMessageCatalog.LOGIN_INVALID_CREDENTIALS);
        }
        // 步骤 5：记录最近登录时间和来源 IP，随后持久化审计字段。
        user.markLogin(LocalDateTime.now(), authSessionGateway.currentRequestIp());
        userRepository.save(user);
        // 步骤 6：创建 Sa-Token 会话并把令牌随登录结果返回给接口层。
        String token = authSessionGateway.login(user);
        return new LoginResult(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType(), token);
    }

    /**
     * 清理当前登录会话。
     */
    public void logoutCurrent() {
        // 步骤 1：会话清理由网关适配具体认证框架，应用服务不直接依赖 Sa-Token。
        authSessionGateway.logoutCurrent();
    }

    /**
     * 读取当前登录用户领域对象。
     * @return 当前登录用户。
     */
    public User currentUser() {
        // 步骤 1：通过会话网关读取当前登录用户 ID，未登录时由网关转换为统一未授权异常。
        Long userId = authSessionGateway.currentLoginId();
        // 步骤 2：从仓储加载用户最新状态，用户已被删除时返回统一未找到语义。
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CURRENT_USER_NOT_FOUND));
    }
}
