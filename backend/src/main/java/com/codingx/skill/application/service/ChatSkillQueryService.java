package com.codingx.skill.application.service;

import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供用户侧聊天技能只读查询服务。
 */
@Service
@RequiredArgsConstructor
public class ChatSkillQueryService {

    /**
     * 技能仓储，用于读取当前启用的聊天技能配置。
     */
    private final ChatSkillRepository chatSkillRepository;

    /**
     * 返回当前启用的技能列表。
     * @return 启用技能列表。
     */
    public List<ChatSkill> listEnabledSkills() {
        // 步骤 1：只返回 enabled=1 且未删除的技能，排序规则由仓储统一处理。
        return chatSkillRepository.findAllEnabled();
    }
}

