package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatQueryTermMapping;
import java.util.List;

/**
 * 定义查询词映射仓储需要提供的最小查询能力。
 */
public interface ChatQueryTermMappingRepository {

    /**
     * 返回当前启用的映射规则集合。
     * @return 映射规则列表。
     */
    List<ChatQueryTermMapping> findEnabledMappings();
}
