package com.codingx.skill.interfaces.response;

/**
 * 技能包文件预览响应，供管理端在线查看文件内容。
 * @param path 技能包内相对路径。
 * @param content 文本内容或图片 data URL。
 * @param truncated true 表示文本预览超过上限后被截断。
 */
public record AdminSkillPackageFileContentResponse(
    String path,
    String content,
    boolean truncated
) {
}

