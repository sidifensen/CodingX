package com.codingx.skill.interfaces.response;

/**
 * 技能包目录树条目响应，供管理端资源管理器展示。
 * @param path 技能包内相对路径。
 * @param name 文件或目录名称。
 * @param directory true 表示目录，false 表示文件。
 * @param size 文件字节大小，目录节点为 null。
 */
public record AdminSkillPackageEntryResponse(
    String path,
    String name,
    boolean directory,
    Long size
) {
}

