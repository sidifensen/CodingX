package com.codingx.expert.domain.repository;

import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.expert.domain.model.ChatExpert;
import java.util.List;

/**
 * 定义聊天专家配置仓储需要提供的持久化能力。
 */
public interface ChatExpertRepository {

    /**
     * 查询所有未删除专家，按 sortNo 与 expertCode 升序输出。
     * @return 专家列表。
     */
    List<ChatExpert> findAll();

    /**
     * 分页查询未删除专家。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 专家分页结果。
     */
    PageResult<ChatExpert> pageQuery(int current, int size);

    /**
     * 查询所有启用专家。
     * @return 启用专家列表。
     */
    List<ChatExpert> findAllEnabled();

    /**
     * 根据主键查询专家。
     * @param id 主键。
     * @return 专家，未命中返回 null。
     */
    ChatExpert findById(Long id);

    /**
     * 根据专家编码查询专家。
     * @param expertCode 专家编码。
     * @return 专家，未命中返回 null。
     */
    ChatExpert findByExpertCode(String expertCode);

    /**
     * 检查专家编码是否存在，更新场景可排除当前主键。
     * @param expertCode 专家编码。
     * @param excludedId 排除主键。
     * @return 是否存在。
     */
    boolean existsByExpertCode(String expertCode, Long excludedId);

    /**
     * 保存专家（新增或更新）。
     * @param expert 专家实体。
     */
    void save(ChatExpert expert);

    /**
     * 逻辑删除专家。
     * @param id 主键。
     */
    void softDeleteById(Long id);

    /**
     * 按运行任务标识读取绑定专家。
     * @param taskId 任务标识（当前等价于 runId）。
     * @return 专家列表。
     */
    List<ChatExpert> findByTaskId(Long taskId);

    /**
     * 绑定任务与专家编码，会先清理旧绑定再写入新绑定。
     * @param taskId 任务标识。
     * @param expertCode 专家编码。
     */
    void bindTaskExpert(Long taskId, String expertCode);
}
