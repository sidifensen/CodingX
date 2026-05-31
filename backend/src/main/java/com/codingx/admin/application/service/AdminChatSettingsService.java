package com.codingx.admin.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.collection.CollUtil;
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

    /**
     * 运行时配置仓储，用于保存管理端提交的配置实体。
     */
    private final ChatRuntimeSettingRepository chatRuntimeSettingRepository;

    /**
     * 运行时配置读取服务，用于刷新缓存并读取管理端最新配置列表。
     */
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 配置加密服务，用于敏感配置明文加密和脱敏展示。
     */
    private final ConfigCryptoService configCryptoService;

    /**
     * 管理端列表是人工校准配置的入口，读取前先刷新运行时缓存，确保外部改库后页面刷新可见。
     */
    public List<ChatRuntimeSetting> listAllSettings() {
        // 步骤 1：先刷新运行时配置缓存，保证管理端看到数据库最新值。
        runtimeSettingService.refresh();
        // 步骤 2：读取全量配置，并在返回前移除敏感配置密文。
        return runtimeSettingService.listAll().stream()
            .map(this::sanitizeForAdmin)
            .toList();
    }

    /**
     * 保存单条运行时配置。
     * @param setting 管理端提交的运行时配置。
     * @return 适合管理端展示的保存结果。
     */
    public ChatRuntimeSetting save(ChatRuntimeSetting setting) {
        // 步骤 1：校验配置键、值类型和值，避免无效配置写入后影响运行时读取。
        validate(setting);
        // 步骤 2：敏感配置统一加密并保留脱敏值，普通配置补齐 secret=false。
        ChatRuntimeSetting normalizedSetting = normalizeSecretSetting(setting);
        // 步骤 3：补齐主键、分类、排序、重启标记和删除标记等默认值。
        ChatRuntimeSetting persisted = normalizedSetting.toBuilder()
            .id(setting.getId() == null ? IdUtil.getSnowflakeNextId() : setting.getId())
            .categoryCode(StrUtil.blankToDefault(normalizedSetting.getCategoryCode(), "general"))
            .createdAt(setting.getCreatedAt() == null ? LocalDateTime.now() : setting.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .sortNo(normalizedSetting.getSortNo() == null ? 0 : normalizedSetting.getSortNo())
            .restartRequired(Boolean.TRUE.equals(normalizedSetting.getRestartRequired()))
            .deleted(normalizedSetting.getDeleted() == null ? 0 : normalizedSetting.getDeleted())
            .build();
        // 步骤 4：保存后刷新运行时缓存，保证新配置立即被后续读取使用。
        chatRuntimeSettingRepository.save(persisted);
        runtimeSettingService.refresh();
        // 步骤 5：返回管理端展示对象，敏感配置不回传密文。
        return sanitizeForAdmin(persisted);
    }

    /**
     * 批量保存运行时配置并返回最新配置列表。
     * @param settings 管理端提交的配置集合，可为空。
     * @return 保存完成后的最新全量配置。
     */
    public List<ChatRuntimeSetting> saveAll(List<ChatRuntimeSetting> settings) {
        // 步骤 1：空集合不写库，直接读取最新配置，适配管理端空提交兜底。
        if (CollUtil.isNotEmpty(settings)) {
            // 步骤 2：逐条复用单条保存逻辑，保证校验、敏感配置加密和缓存刷新策略一致。
            for (ChatRuntimeSetting setting : settings) {
                save(setting);
            }
        }
        // 步骤 3：批量保存完成后读取最新全量配置，前端用返回值覆盖本地设置表格。
        return listAllSettings();
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
        runtimeSettingService.refresh();
        ChatRuntimeSetting existing = runtimeSettingService.listAll().stream()
            .filter(item -> StrUtil.equals(item.getSettingKey(), setting.getSettingKey()))
            .findFirst()
            .orElse(null);
        if (existing != null && StrUtil.isBlank(setting.getSettingValue())) {
            return setting.toBuilder()
                .settingValue("")
                .encryptedValue(existing.getEncryptedValue())
                .maskedValue(existing.getMaskedValue())
                .encryptionAlgorithm(existing.getEncryptionAlgorithm())
                .encryptionKeyVersion(existing.getEncryptionKeyVersion())
                .secret(true)
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

    /**
     * 管理端返回前移除敏感项密文，只保留脱敏值与空白输入槽位。
     * @param setting 原始配置。
     * @return 适合管理端展示的配置。
     */
    private ChatRuntimeSetting sanitizeForAdmin(ChatRuntimeSetting setting) {
        if (setting == null || !Boolean.TRUE.equals(setting.getSecret())) {
            return setting;
        }
        return setting.toBuilder()
            .settingValue("")
            .encryptedValue(null)
            .build();
    }
}
