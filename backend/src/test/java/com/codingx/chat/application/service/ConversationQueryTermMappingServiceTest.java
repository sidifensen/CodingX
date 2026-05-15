package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证查询词映射服务会在改写前完成术语归一化。
 */
@ExtendWith(MockitoExtension.class)
class ConversationQueryTermMappingServiceTest {

    @Mock
    private ChatQueryTermMappingRepository chatQueryTermMappingRepository;

    @InjectMocks
    private ConversationQueryTermMappingService conversationQueryTermMappingService;

    /**
     * 命中术语映射时应把口语化短词替换成标准词。
     */
    @Test
    void normalizeAppliesEnabledMappings() {
        when(chatQueryTermMappingRepository.findEnabledMappings()).thenReturn(List.of(
            ChatQueryTermMapping.builder().sourceTerm("oa").targetTerm("OA系统").sortNo(1).enabled(1).build(),
            ChatQueryTermMapping.builder().sourceTerm("rag").targetTerm("检索增强生成").sortNo(2).enabled(1).build()
        ));

        String normalized = conversationQueryTermMappingService.normalize("oa 里接入 rag 怎么做");

        assertEquals("OA系统 里接入 检索增强生成 怎么做", normalized);
    }
}
