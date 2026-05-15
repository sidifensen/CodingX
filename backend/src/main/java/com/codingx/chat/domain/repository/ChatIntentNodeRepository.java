package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;

/**
 * 定义意图树节点仓储需要提供的最小持久化能力。
 */
public interface ChatIntentNodeRepository {

    /**
     * 保存或更新意图节点。
     * @param node 意图节点记录。
     */
    void save(ChatIntentNode node);

    /**
     * 查询当前启用的意图节点集合。
     * @return 节点集合。
     */
    List<ChatIntentNode> findEnabledNodes();

    /**
     * 查询全部未删除的意图节点集合，供后台管理使用。
     * @return 节点集合。
     */
    List<ChatIntentNode> findAllNodes();

    /**
     * 按意图编码查询单个节点。
     * @param intentCode 意图编码。
     * @return 意图节点；不存在时返回 null。
     */
    ChatIntentNode findByIntentCode(String intentCode);

    /**
     * 按主键查询未删除节点，供管理端更新和删除保护使用。
     * @param id 节点主键。
     * @return 意图节点；不存在或已删除时返回 null。
     */
    ChatIntentNode findById(Long id);

    /**
     * 判断业务编码是否已被其他未删除节点占用。
     * @param intentCode 意图编码。
     * @param excludedId 更新当前节点时需要排除的主键；新增时传 null。
     * @return true 表示存在重复编码。
     */
    boolean existsByIntentCode(String intentCode, Long excludedId);

    /**
     * 判断指定父编码下是否仍存在未删除子节点，避免删除后产生孤儿树。
     * @param parentCode 父节点业务编码。
     * @return true 表示存在子节点。
     */
    boolean hasChildren(String parentCode);

    /**
     * 按主键逻辑删除节点，保留历史引用关系。
     * @param id 节点主键。
     */
    void softDeleteById(Long id);
}
