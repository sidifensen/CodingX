package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责在改写前对用户问题做术语归一化，提升意图识别和搜索稳定性。
 */
@Service
@RequiredArgsConstructor
public class ConversationQueryTermMappingService {

    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;
    private final ConversationQueryTermMappingCacheManager conversationQueryTermMappingCacheManager;

    /**
     * 对输入文本执行术语归一化替换。
     * @param text 原始文本。
     * @return 归一化后的文本。
     */
    public String normalize(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String normalized = text;
        List<ChatQueryTermMapping> mappings = conversationQueryTermMappingCacheManager.getMappings(
            chatQueryTermMappingRepository::findEnabledMappings
        );
        for (ChatQueryTermMapping mapping : mappings) {
            String source = mapping.getSourceTerm();
            String target = mapping.getTargetTerm();
            if (StrUtil.hasBlank(source, target)) {
                continue;
            }
            normalized = applyMapping(normalized, source, target);
        }
        return normalized;
    }

    /**
     * 只替换源词，避免命中目标词后重复替换。
     * @param text 原始文本。
     * @param sourceTerm 源词。
     * @param targetTerm 目标词。
     * @return 替换结果。
     */
    private String applyMapping(String text, String sourceTerm, String targetTerm) {
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        int length = text.length();
        while (cursor < length) {
            int hit = text.indexOf(sourceTerm, cursor);
            if (hit < 0) {
                builder.append(text, cursor, length);
                break;
            }
            builder.append(text, cursor, hit);
            boolean alreadyTarget = hit + targetTerm.length() <= length && text.startsWith(targetTerm, hit);
            if (alreadyTarget) {
                builder.append(text, hit, hit + targetTerm.length());
                cursor = hit + targetTerm.length();
            } else {
                builder.append(targetTerm);
                cursor = hit + sourceTerm.length();
            }
        }
        return builder.toString();
    }
}
