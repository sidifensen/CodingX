package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.interfaces.response.PageResult;
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
     * 分页查询映射规则，支持按源词或目标词关键字过滤。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param keyword 查询关键字。
     * @return 分页结果。
     */
    PageResult<ChatQueryTermMapping> pageQuery(int current, int size, String keyword);

    /**
     * 按主键查询映射规则。
     * @param id 主键。
     * @return 映射规则；不存在时返回 null。
     */
    ChatQueryTermMapping findById(Long id);

    /**
     * 保存或更新映射规则。
     * @param mapping 映射规则。
     */
    void save(ChatQueryTermMapping mapping);

    /**
     * 按主键逻辑删除映射规则。
     * @param id 主键。
     */
    void softDeleteById(Long id);
}
