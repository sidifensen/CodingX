package com.codingx.auth.application.service;

import cn.hutool.core.util.StrUtil;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * CLI 授权短期内存状态存储，适合单实例本地部署；多实例时可替换为 Redis 实现。
 */
@Component
public class InMemoryCliAuthStateStore implements CliAuthStateStore {

    /**
     * 授权码索引，key 为一次性 code。
     */
    private final Map<String, CliAuthorization> authorizationByCode = new HashMap<>();

    /**
     * 设备授权索引，key 为 CLI 内部 deviceCode。
     */
    private final Map<String, CliDeviceAuthorization> deviceByDeviceCode = new HashMap<>();

    /**
     * 设备授权索引，key 为用户可读 userCode。
     */
    private final Map<String, String> deviceCodeByUserCode = new HashMap<>();

    @Override
    public synchronized void saveAuthorization(CliAuthorization authorization) {
        cleanupExpired();
        authorizationByCode.put(authorization.code(), authorization);
    }

    @Override
    public synchronized Optional<CliAuthorization> consumeAuthorization(String code) {
        cleanupExpired();
        if (StrUtil.isBlank(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(authorizationByCode.remove(code.trim()));
    }

    @Override
    public synchronized void saveDeviceAuthorization(CliDeviceAuthorization deviceAuthorization) {
        cleanupExpired();
        deviceByDeviceCode.put(deviceAuthorization.deviceCode(), deviceAuthorization);
        deviceCodeByUserCode.put(normalizeUserCode(deviceAuthorization.userCode()), deviceAuthorization.deviceCode());
    }

    @Override
    public synchronized Optional<CliDeviceAuthorization> findDeviceByUserCode(String userCode) {
        cleanupExpired();
        String deviceCode = deviceCodeByUserCode.get(normalizeUserCode(userCode));
        return Optional.ofNullable(deviceByDeviceCode.get(deviceCode));
    }

    @Override
    public synchronized Optional<CliDeviceAuthorization> findDeviceByDeviceCode(String deviceCode) {
        cleanupExpired();
        if (StrUtil.isBlank(deviceCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(deviceByDeviceCode.get(deviceCode.trim()));
    }

    @Override
    public synchronized void updateDeviceAuthorization(CliDeviceAuthorization deviceAuthorization) {
        cleanupExpired();
        if (!deviceByDeviceCode.containsKey(deviceAuthorization.deviceCode())) {
            return;
        }
        deviceByDeviceCode.put(deviceAuthorization.deviceCode(), deviceAuthorization);
        deviceCodeByUserCode.put(normalizeUserCode(deviceAuthorization.userCode()), deviceAuthorization.deviceCode());
    }

    @Override
    public synchronized Optional<CliDeviceAuthorization> consumeDeviceAuthorization(String deviceCode) {
        cleanupExpired();
        if (StrUtil.isBlank(deviceCode)) {
            return Optional.empty();
        }
        CliDeviceAuthorization removed = deviceByDeviceCode.remove(deviceCode.trim());
        if (removed != null) {
            deviceCodeByUserCode.remove(normalizeUserCode(removed.userCode()));
        }
        return Optional.ofNullable(removed);
    }

    /**
     * 清理过期状态，防止长期运行时内存增长。
     */
    private void cleanupExpired() {
        Instant now = Instant.now();
        authorizationByCode.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        deviceByDeviceCode.entrySet().removeIf(entry -> {
            boolean expired = !entry.getValue().expiresAt().isAfter(now);
            if (expired) {
                deviceCodeByUserCode.remove(normalizeUserCode(entry.getValue().userCode()));
            }
            return expired;
        });
    }

    /**
     * 归一化用户验证码，允许前端输入大小写不同或带空格横杠。
     *
     * @param userCode 原始验证码。
     * @return 可作为索引的验证码。
     */
    private String normalizeUserCode(String userCode) {
        return StrUtil.trimToEmpty(userCode).replace("-", "").replace(" ", "").toUpperCase();
    }
}

