package com.codingx.expert.application.service;

import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供用户侧聊天专家只读查询服务。
 */
@Service
@RequiredArgsConstructor
public class ChatExpertQueryService {

    /**
     * 专家配置仓储，用于读取当前启用且未删除的专家。
     */
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 返回当前启用的专家列表。
     * @return 启用专家列表。
     */
    public List<ChatExpert> listEnabledExperts() {
        // 步骤 1：只返回 enabled=1 且未删除的专家配置，排序规则由仓储统一处理。
        return chatExpertRepository.findAllEnabled();
    }
}
