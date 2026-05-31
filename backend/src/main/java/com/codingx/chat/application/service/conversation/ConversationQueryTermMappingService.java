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

    /** 关键词映射仓储，用于在缓存未命中时读取启用规则。 */
    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;
    /** 关键词映射缓存管理器，用于复用启用规则并降低改写前数据库访问频率。 */
    private final ConversationQueryTermMappingCacheManager conversationQueryTermMappingCacheManager;

    /**
     * 对输入文本执行术语归一化替换。
     * @param text 原始文本。
     * @return 归一化后的文本。
     */
    public String normalize(String text) {
        // 步骤 1：空文本不做映射，保持调用方原始空值语义。
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String normalized = text;
        // 步骤 2：从缓存读取启用映射，缓存缺失时由仓储加载，避免每次改写都查库。
        List<ChatQueryTermMapping> mappings = conversationQueryTermMappingCacheManager.getMappings(
            chatQueryTermMappingRepository::findEnabledMappings
        );
        for (ChatQueryTermMapping mapping : mappings) {
            // 步骤 3：仅处理启用且精确匹配的规则，非法源词或目标词直接跳过。
            if (mapping.getEnabled() == null || mapping.getEnabled() != 1) {
                continue;
            }
            // 当前版本仅支持精确匹配，其他匹配类型保留给后续扩展。
            if (mapping.getMatchType() != null && mapping.getMatchType() != 1) {
                continue;
            }
            String source = mapping.getSourceTerm();
            String target = mapping.getTargetTerm();
            if (StrUtil.hasBlank(source, target)) {
                continue;
            }
            // 步骤 4：按规则顺序逐个替换，后续规则基于上一轮归一化结果继续处理。
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
