package com.codingx.admin.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.support.ConfigCryptoService;
import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天运行时配置后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatSettingsService {

    private final ChatRuntimeSettingRepository chatRuntimeSettingRepository;
    private final RuntimeSettingService runtimeSettingService;
    private final ConfigCryptoService configCryptoService;

    /**
     * 管理端列表是人工校准配置的入口，读取前先刷新运行时缓存，确保外部改库后页面刷新可见。
     */
    public List<ChatRuntimeSetting> listAllSettings() {
        runtimeSettingService.refresh();
        return runtimeSettingService.listAll();
    }

    public ChatRuntimeSetting save(ChatRuntimeSetting setting) {
        validate(setting);
        ChatRuntimeSetting normalizedSetting = normalizeSecretSetting(setting);
        ChatRuntimeSetting persisted = normalizedSetting.toBuilder()
            .id(setting.getId() == null ? IdUtil.getSnowflakeNextId() : setting.getId())
            .categoryCode(StrUtil.blankToDefault(normalizedSetting.getCategoryCode(), "general"))
            .createdAt(setting.getCreatedAt() == null ? LocalDateTime.now() : setting.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .sortNo(normalizedSetting.getSortNo() == null ? 0 : normalizedSetting.getSortNo())
            .restartRequired(Boolean.TRUE.equals(normalizedSetting.getRestartRequired()))
            .deleted(normalizedSetting.getDeleted() == null ? 0 : normalizedSetting.getDeleted())
            .build();
        chatRuntimeSettingRepository.save(persisted);
        runtimeSettingService.refresh();
        return persisted;
    }

    /**
     * 敏感配置在保存前统一加密并生成脱敏值，避免仓储层接触明文写库策略。
     * @param setting 原始配置。
     * @return 归一化后的配置。
     */
    private ChatRuntimeSetting normalizeSecretSetting(ChatRuntimeSetting setting) {
        if (!Boolean.TRUE.equals(setting.getSecret())) {
            return setting.toBuilder()
                .secret(false)
                .build();
        }
        String plainText = StrUtil.nullToEmpty(setting.getSettingValue());
        return setting.toBuilder()
            .settingValue("")
            .encryptedValue(configCryptoService.encrypt(plainText))
            .maskedValue(configCryptoService.mask(plainText))
            .encryptionAlgorithm(configCryptoService.algorithm())
            .encryptionKeyVersion(configCryptoService.keyVersion())
            .secret(true)
            .build();
    }

    /**
     * 校验管理端提交的运行时配置，避免落入空键或空值导致运行时异常。
     * @param setting 运行时配置实体。
     */
    private void validate(ChatRuntimeSetting setting) {
        if (setting == null) {
            throw new BusinessException("SETTING_INVALID", ErrorMessageCatalog.CHAT_SETTING_REQUIRED);
        }
        if (StrUtil.isBlank(setting.getSettingKey())) {
            throw new BusinessException("SETTING_INVALID", ErrorMessageCatalog.CHAT_SETTING_KEY_REQUIRED);
        }
        if (StrUtil.isBlank(setting.getValueType())) {
            throw new BusinessException("SETTING_INVALID", ErrorMessageCatalog.CHAT_SETTING_VALUE_TYPE_REQUIRED);
        }
        if (setting.getSettingValue() == null && !Boolean.TRUE.equals(setting.getSecret())) {
            throw new BusinessException("SETTING_INVALID", ErrorMessageCatalog.CHAT_SETTING_VALUE_REQUIRED);
        }
    }
}
