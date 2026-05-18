package com.codingx.skill.interfaces.response;

/**
 * 定义技能包文件预览响应，供管理端在线查看文件内容。
 * @param path 归档内相对路径。
 * @param content 文本内容。
 * @param truncated 是否被截断。
 */
public record AdminSkillPackageFileContentResponse(
    String path,
    String content,
    boolean truncated
) {
}

