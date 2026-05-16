package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天技能后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatSkillService {

    private final ChatSkillRepository chatSkillRepository;

    /**
     * 返回技能列表。
     * @return 技能列表。
     */
    public List<ChatSkill> listAll() {
        return chatSkillRepository.findAll();
    }

    /**
     * 创建技能。
     * @param request 请求对象。
     * @return 新增后的技能。
     */
    public ChatSkill create(ChatSkill request) {
        validateRequired(request);
        String normalizedSkillCode = request.getSkillCode().trim();
        if (chatSkillRepository.existsBySkillCode(normalizedSkillCode, null)) {
            throw new BusinessException("CHAT_SKILL_DUPLICATE_CODE", "技能编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSkill persisted = request.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .skillCode(normalizedSkillCode)
            .displayName(request.getDisplayName().trim())
            .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
            .sortNo(request.getSortNo() == null ? 0 : request.getSortNo())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), "built-in"))
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 更新技能。
     * @param id 主键。
     * @param request 请求对象。
     * @return 更新后的技能。
     */
    public ChatSkill update(Long id, ChatSkill request) {
        ChatSkill existing = chatSkillRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("技能不存在");
        }
        validateRequired(request);
        String normalizedSkillCode = request.getSkillCode().trim();
        if (chatSkillRepository.existsBySkillCode(normalizedSkillCode, id)) {
            throw new BusinessException("CHAT_SKILL_DUPLICATE_CODE", "技能编码已存在");
        }
        ChatSkill persisted = request.toBuilder()
            .id(id)
            .skillCode(normalizedSkillCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), existing.getSourceType()))
            .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
            .sortNo(request.getSortNo() == null ? existing.getSortNo() : request.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted())
            .build();
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 删除技能。
     * @param id 主键。
     */
    public void delete(Long id) {
        ChatSkill existing = chatSkillRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("技能不存在");
        }
        chatSkillRepository.softDeleteById(id);
    }

    private void validateRequired(ChatSkill request) {
        if (request == null) {
            throw new BusinessException("CHAT_SKILL_INVALID", "技能信息不能为空");
        }
        if (StrUtil.isBlank(request.getSkillCode())) {
            throw new BusinessException("CHAT_SKILL_INVALID", "技能编码不能为空");
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_SKILL_INVALID", "技能名称不能为空");
        }
    }
}
