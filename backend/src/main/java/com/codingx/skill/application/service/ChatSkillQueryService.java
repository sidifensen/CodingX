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

    private final ChatSkillRepository chatSkillRepository;

    /**
     * 返回当前启用的技能列表。
     * @return 启用技能列表。
     */
    public List<ChatSkill> listEnabledSkills() {
        return chatSkillRepository.findAllEnabled();
    }
}

