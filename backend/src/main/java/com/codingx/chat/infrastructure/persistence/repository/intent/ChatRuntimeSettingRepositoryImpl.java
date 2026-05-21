package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatRuntimeSettingDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatRuntimeSettingMapper;
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
        return chatRuntimeSettingMapper.selectList(new LambdaQueryWrapper<ChatRuntimeSettingDO>()
                .eq(ChatRuntimeSettingDO::getDeleted, 0)
                .orderByAsc(ChatRuntimeSettingDO::getCategoryCode)
                .orderByAsc(ChatRuntimeSettingDO::getSortNo)
                .orderByAsc(ChatRuntimeSettingDO::getSettingKey))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void save(ChatRuntimeSetting setting) {
        ChatRuntimeSettingDO dataObject = new ChatRuntimeSettingDO();
        dataObject.setId(setting.getId());
        dataObject.setSettingKey(setting.getSettingKey());
        dataObject.setSettingValue(setting.getSettingValue());
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
}
