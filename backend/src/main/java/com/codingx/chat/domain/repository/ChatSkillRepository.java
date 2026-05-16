package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatSkill;
import java.util.List;

/**
 * 定义聊天技能配置仓储需要提供的持久化能力。
 */
public interface ChatSkillRepository {

    /**
     * 查询所有未删除技能，按 sortNo 与 skillCode 升序输出。
     * @return 技能列表。
     */
    List<ChatSkill> findAll();

    /**
     * 查询所有启用技能，按 sortNo 与 skillCode 升序输出。
     * @return 启用技能列表。
     */
    List<ChatSkill> findAllEnabled();

    /**
     * 根据主键查询技能。
     * @param id 主键。
     * @return 技能，未命中返回 null。
     */
    ChatSkill findById(Long id);

    /**
     * 根据技能编码查询技能。
     * @param skillCode 技能编码。
     * @return 技能，未命中返回 null。
     */
    ChatSkill findBySkillCode(String skillCode);

    /**
     * 检查技能编码是否存在，更新场景可排除当前主键。
     * @param skillCode 技能编码。
     * @param excludedId 排除主键。
     * @return 是否存在。
     */
    boolean existsBySkillCode(String skillCode, Long excludedId);

    /**
     * 保存技能（新增或更新）。
     * @param skill 技能实体。
     */
    void save(ChatSkill skill);

    /**
     * 逻辑删除技能。
     * @param id 主键。
     */
    void softDeleteById(Long id);

    /**
     * 按运行任务标识读取绑定技能。
     * @param taskId 任务标识（当前等价于 runId）。
     * @return 技能列表。
     */
    List<ChatSkill> findByTaskId(Long taskId);

    /**
     * 绑定任务与技能编码列表，会先清理旧绑定再写入新绑定。
     * @param taskId 任务标识。
     * @param skillCodes 技能编码列表。
     */
    void bindTaskSkills(Long taskId, List<String> skillCodes);
}
