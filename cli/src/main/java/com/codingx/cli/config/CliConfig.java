package com.codingx.cli.config;

/**
 * CLI 本机配置快照，令牌只允许存放在用户主目录配置文件中。
 *
 * @param serverUrl 后端服务地址。
 * @param token satoken 登录令牌。
 * @param approvalPolicy 默认审批策略。
 * @param lastSessionId 最近会话标识，可为空。
 */
public record CliConfig(
    String serverUrl,
    String token,
    String approvalPolicy,
    String lastSessionId
) {
    /**
     * 构造缺省配置，便于首次运行时给出稳定默认值。
     *
     * @return 默认配置。
     */
    public static CliConfig defaults() {
        return new CliConfig("http://localhost:5001", "", "conservative", null);
    }
}
