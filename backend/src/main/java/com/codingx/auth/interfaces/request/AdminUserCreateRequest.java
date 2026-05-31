package com.codingx.auth.interfaces.request;

/**
 * 管理端新增用户请求体。
 * @param username 登录用户名，创建后作为账号唯一标识。
 * @param displayName 展示名称，管理端和前端会话都会展示。
 * @param password 初始明文密码，服务层会转换为哈希后保存。
 * @param userType 用户类型，为空时默认普通用户。
 * @param status 初始用户状态，决定账号是否可立即登录。
 * @param email 邮箱，可为空但非空时需满足唯一性。
 * @param phone 手机号，可为空。
 * @param avatarUrl 头像地址，可为空。
 */
public record AdminUserCreateRequest(
    String username, // 管理端填写的登录用户名，创建后作为账号唯一标识。
    String displayName, // 用户展示名称，前端会话和管理端列表都会展示。
    String password, // 初始明文密码，仅用于创建时生成哈希，禁止持久化明文。
    String userType, // 用户类型枚举名称，可为空；服务层为空时默认普通用户。
    String status, // 初始用户状态枚举名称，可为空；服务层按可写状态解析。
    String email, // 用户邮箱，可为空；非空时服务层会做唯一性检查。
    String phone, // 用户手机号，可为空；当前只做首尾空白规整。
    String avatarUrl // 用户头像地址，可为空；当前只做首尾空白规整。
) {
}
