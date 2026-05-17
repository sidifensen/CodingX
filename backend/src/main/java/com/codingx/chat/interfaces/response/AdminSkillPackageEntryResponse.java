package com.codingx.chat.interfaces.response;

/**
 * 定义技能包目录树条目响应，供管理端资源管理器展示。
 * @param path 归档内相对路径。
 * @param name 文件或目录名称。
 * @param directory 是否目录。
 * @param size 文件字节大小，目录为 null。
 */
public record AdminSkillPackageEntryResponse(
    String path,
    String name,
    boolean directory,
    Long size
) {
}
