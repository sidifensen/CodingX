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

    private final ChatExpertRepository chatExpertRepository;

    /**
     * 返回当前启用的专家列表。
     * @return 启用专家列表。
     */
    public List<ChatExpert> listEnabledExperts() {
        return chatExpertRepository.findAllEnabled();
    }
}
