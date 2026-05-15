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

    /**
     * 返回全部映射规则，供后台管理使用。
     * @return 映射规则列表。
     */
    List<ChatQueryTermMapping> findAllMappings();

    /**
     * 保存或更新映射规则。
     * @param mapping 映射规则。
     */
    void save(ChatQueryTermMapping mapping);
}
