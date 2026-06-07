package com.codingx.auth.application.service;

import java.time.Instant;
import java.util.Optional;

/**
 * CLI 授权短期状态存储契约，隔离应用服务与内存/Redis 等具体实现。
 */
public interface CliAuthStateStore {

    /**
     * 保存授权码状态。
     *
     * @param authorization 授权码快照。
     */
    void saveAuthorization(CliAuthorization authorization);

    /**
     * 一次性消费授权码；返回后状态必须从存储中移除。
     *
     * @param code 授权码。
     * @return 授权码状态，过期或不存在时为空。
     */
    Optional<CliAuthorization> consumeAuthorization(String code);

    /**
     * 保存设备授权状态。
     *
     * @param deviceAuthorization 设备授权快照。
     */
    void saveDeviceAuthorization(CliDeviceAuthorization deviceAuthorization);

    /**
     * 按用户可读验证码读取设备授权。
     *
     * @param userCode 用户可读验证码。
     * @return 设备授权状态。
     */
    Optional<CliDeviceAuthorization> findDeviceByUserCode(String userCode);

    /**
     * 按内部设备码读取设备授权。
     *
     * @param deviceCode 内部设备码。
     * @return 设备授权状态。
     */
    Optional<CliDeviceAuthorization> findDeviceByDeviceCode(String deviceCode);

    /**
     * 保存设备授权更新后的状态。
     *
     * @param deviceAuthorization 更新后的设备授权。
     */
    void updateDeviceAuthorization(CliDeviceAuthorization deviceAuthorization);

    /**
     * 消费设备授权，成功后必须移除 deviceCode 和 userCode 两个索引。
     *
     * @param deviceCode 内部设备码。
     * @return 设备授权状态。
     */
    Optional<CliDeviceAuthorization> consumeDeviceAuthorization(String deviceCode);

    /**
     * CLI 授权码短期状态。
     *
     * @param code 一次性授权码。
     * @param userId 当前浏览器登录用户主键。
     * @param state CLI state。
     * @param redirectUri CLI 本机回调地址。
     * @param codeChallenge PKCE challenge。
     * @param expiresAt 过期时间。
     */
    record CliAuthorization(
        String code, // 一次性授权码。
        Long userId, // 授权用户主键。
        String state, // CLI state。
        String redirectUri, // 本机回调地址。
        String codeChallenge, // PKCE challenge。
        Instant expiresAt // 过期时间。
    ) {
    }

    /**
     * CLI 设备码短期状态。
     *
     * @param deviceCode CLI 轮询使用的内部设备码。
     * @param userCode 用户输入的短验证码。
     * @param userId 浏览器确认授权后的用户主键，pending 时为空。
     * @param expiresAt 过期时间。
     */
    record CliDeviceAuthorization(
        String deviceCode, // 内部设备码。
        String userCode, // 用户可读验证码。
        Long userId, // 授权用户主键，pending 时为空。
        Instant expiresAt // 过期时间。
    ) {
        /**
         * 返回绑定用户后的设备授权快照，保持 record 不可变。
         *
         * @param nextUserId 授权用户主键。
         * @return 更新后的设备授权。
         */
        public CliDeviceAuthorization approve(Long nextUserId) {
            return new CliDeviceAuthorization(deviceCode, userCode, nextUserId, expiresAt);
        }
    }
}

