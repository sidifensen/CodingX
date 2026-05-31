package com.codingx.skill.interfaces.response;

/**
 * 技能包目录树条目响应，供管理端资源管理器展示。
 * @param path 技能包内规范化相对路径，目录和文件都使用该路径定位。
 * @param name 文件或目录名称，取自 path 最后一段。
 * @param directory true 表示目录，false 表示文件。
 * @param size 文件字节大小，目录节点固定为 null。
 */
public record AdminSkillPackageEntryResponse(
    String path, // 技能包内规范化相对路径，目录和文件都使用该路径定位。
    String name, // 文件或目录名称，取自 path 最后一段。
    boolean directory, // true 表示目录节点，false 表示文件节点。
    Long size // 文件字节大小，目录节点固定为 null。
) {
}

