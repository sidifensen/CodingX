package com.codingx.chat.infrastructure.persistence.repository.intent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.intent.ChatRuntimeSettingDO;
import com.codingx.chat.infrastructure.persistence.mapper.intent.ChatRuntimeSettingMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天运行时配置仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatRuntimeSettingRepositoryImpl implements ChatRuntimeSettingRepository {

    private final ChatRuntimeSettingMapper chatRuntimeSettingMapper;

    @Override
    public List<ChatRuntimeSetting> findAll() {
        try {
            return selectAllWithCurrentSchema();
        } catch (RuntimeException exception) {
            if (!isMissingEncryptedColumns(exception)) {
                throw exception;
            }
            return selectAllWithLegacySchema();
        }
    }

    @Override
    public ChatRuntimeSetting findBySettingKey(String settingKey) {
        if (settingKey == null) {
            return null;
        }
        try {
            ChatRuntimeSettingDO dataObject = chatRuntimeSettingMapper.selectOne(new LambdaQueryWrapper<ChatRuntimeSettingDO>()
                .eq(ChatRuntimeSettingDO::getSettingKey, settingKey)
                .eq(ChatRuntimeSettingDO::getDeleted, 0)
                .last("LIMIT 1"));
            return dataObject == null ? null : toDomain(dataObject);
        } catch (RuntimeException exception) {
            if (!isMissingEncryptedColumns(exception)) {
                throw exception;
            }
            return findBySettingKeyWithLegacySchema(settingKey);
        }
    }

    @Override
    public void save(ChatRuntimeSetting setting) {
        ChatRuntimeSettingDO dataObject = new ChatRuntimeSettingDO();
        dataObject.setId(setting.getId());
        dataObject.setSettingKey(setting.getSettingKey());
        dataObject.setSettingValue(setting.getSettingValue());
        dataObject.setEncryptedValue(setting.getEncryptedValue());
        dataObject.setSecret(setting.getSecret());
        dataObject.setMaskedValue(setting.getMaskedValue());
        dataObject.setEncryptionAlgorithm(setting.getEncryptionAlgorithm());
        dataObject.setEncryptionKeyVersion(setting.getEncryptionKeyVersion());
        dataObject.setValueType(setting.getValueType());
        dataObject.setCategoryCode(setting.getCategoryCode());
        dataObject.setDescription(setting.getDescription());
        dataObject.setSortNo(setting.getSortNo());
        dataObject.setRestartRequired(setting.getRestartRequired());
        dataObject.setCreatedAt(setting.getCreatedAt());
        dataObject.setUpdatedAt(setting.getUpdatedAt());
        dataObject.setDeleted(setting.getDeleted());
        if (chatRuntimeSettingMapper.selectById(setting.getId()) == null) {
            chatRuntimeSettingMapper.insert(dataObject);
        } else {
            chatRuntimeSettingMapper.updateById(dataObject);
        }
    }

    private ChatRuntimeSetting toDomain(ChatRuntimeSettingDO dataObject) {
        return ChatRuntimeSetting.builder()
            .id(dataObject.getId())
            .settingKey(dataObject.getSettingKey())
            .settingValue(dataObject.getSettingValue())
            .encryptedValue(dataObject.getEncryptedValue())
            .secret(dataObject.getSecret())
            .maskedValue(dataObject.getMaskedValue())
            .encryptionAlgorithm(dataObject.getEncryptionAlgorithm())
            .encryptionKeyVersion(dataObject.getEncryptionKeyVersion())
            .valueType(dataObject.getValueType())
            .categoryCode(dataObject.getCategoryCode())
            .description(dataObject.getDescription())
            .sortNo(dataObject.getSortNo())
            .restartRequired(dataObject.getRestartRequired())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }

    /**
     * 使用当前完整表结构读取系统配置。
     * @return 配置列表。
     */
    private List<ChatRuntimeSetting> selectAllWithCurrentSchema() {
        return chatRuntimeSettingMapper.selectList(new LambdaQueryWrapper<ChatRuntimeSettingDO>()
                .eq(ChatRuntimeSettingDO::getDeleted, 0)
                .orderByAsc(ChatRuntimeSettingDO::getCategoryCode)
                .orderByAsc(ChatRuntimeSettingDO::getSortNo)
                .orderByAsc(ChatRuntimeSettingDO::getSettingKey))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 兼容旧数据库结构，在敏感字段 migration 尚未执行前先回退为旧字段读取，避免应用无法启动。
     * @return 配置列表。
     */
    private List<ChatRuntimeSetting> selectAllWithLegacySchema() {
        return chatRuntimeSettingMapper.selectMaps(new LambdaQueryWrapper<ChatRuntimeSettingDO>()
                .eq(ChatRuntimeSettingDO::getDeleted, 0)
                .orderByAsc(ChatRuntimeSettingDO::getCategoryCode)
                .orderByAsc(ChatRuntimeSettingDO::getSortNo)
                .orderByAsc(ChatRuntimeSettingDO::getSettingKey))
            .stream()
            .map(row -> ChatRuntimeSetting.builder()
                .id(row.get("id") == null ? null : Long.valueOf(row.get("id").toString()))
                .settingKey(stringValue(row.get("setting_key")))
                .settingValue(stringValue(row.get("setting_value")))
                .valueType(stringValue(row.get("value_type")))
                .categoryCode(stringValue(row.get("category_code")))
                .description(stringValue(row.get("description")))
                .sortNo(intValue(row.get("sort_no")))
                .restartRequired(booleanValue(row.get("restart_required")))
                .deleted(intValue(row.get("deleted")))
                .build())
            .toList();
    }

    /**
     * 兼容旧数据库结构按键读取单条配置，便于敏感配置在 migration 前也能保留原有明文值。
     * @param settingKey 配置键。
     * @return 命中的配置；不存在时返回 null。
     */
    private ChatRuntimeSetting findBySettingKeyWithLegacySchema(String settingKey) {
        return chatRuntimeSettingMapper.selectMaps(new LambdaQueryWrapper<ChatRuntimeSettingDO>()
                .eq(ChatRuntimeSettingDO::getSettingKey, settingKey)
                .eq(ChatRuntimeSettingDO::getDeleted, 0)
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .map(row -> ChatRuntimeSetting.builder()
                .id(row.get("id") == null ? null : Long.valueOf(row.get("id").toString()))
                .settingKey(stringValue(row.get("setting_key")))
                .settingValue(stringValue(row.get("setting_value")))
                .valueType(stringValue(row.get("value_type")))
                .categoryCode(stringValue(row.get("category_code")))
                .description(stringValue(row.get("description")))
                .sortNo(intValue(row.get("sort_no")))
                .restartRequired(booleanValue(row.get("restart_required")))
                .deleted(intValue(row.get("deleted")))
                .build())
            .orElse(null);
    }

    private boolean isMissingEncryptedColumns(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("encrypted_value");
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer intValue(Object value) {
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private Boolean booleanValue(Object value) {
        return value == null ? null : Boolean.valueOf(value.toString());
    }
}
