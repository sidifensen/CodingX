package com.codingx.auth.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.interfaces.response.AdminUserDetailResponse;
import com.codingx.auth.interfaces.response.AdminUserPageResponse;
import com.codingx.auth.interfaces.response.AdminUserSummaryResponse;
import com.codingx.auth.interfaces.response.LoginResponse;
import com.codingx.auth.interfaces.response.MeResponse;
import org.springframework.stereotype.Service;

/**
 * 用户视图服务，统一负责认证与管理端用户响应对象投影。
 */
@Service
public class UserViewService {

    /**
     * 将登录用例结果转换为登录接口响应。
     * @param result 登录服务返回的用户身份与会话令牌。
     * @return 登录响应对象。
     */
    public LoginResponse toLoginResponse(LoginResult result) {
        // 步骤 1：保留应用服务已校验通过的用户身份字段，不在 Controller 中重复拆字段。
        // 步骤 2：把会话令牌一并返回给前端，用于后续接口鉴权。
        return new LoginResponse(
            result.userId(),
            result.username(),
            result.displayName(),
            result.userType(),
            result.token()
        );
    }

    /**
     * 将当前登录用户转换为“我的信息”响应。
     * @param user 当前登录用户领域对象。
     * @return 当前用户响应对象。
     */
    public MeResponse toMeResponse(User user) {
        // 步骤 1：仅投影前端身份展示需要的字段，避免泄露密码哈希、邮箱、手机号等管理端数据。
        // 步骤 2：保留枚举类型原值，交由统一序列化层输出。
        return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType());
    }

    /**
     * 将管理端用户分页视图转换为接口分页响应。
     * @param pageView 管理端用户分页视图。
     * @return 管理端用户分页响应。
     */
    public AdminUserPageResponse toAdminPageResponse(AdminUserPageView pageView) {
        // 步骤 1：分页元数据来自仓储查询结果，视图层只保持原值透传。
        // 步骤 2：列表记录逐项转换，并集中生成中文状态和类型标签。
        return AdminUserPageResponse.builder()
            .records(pageView.records().stream().map(this::toAdminSummary).toList())
            .total(pageView.total())
            .current(pageView.current())
            .size(pageView.size())
            .pages(pageView.pages())
            .build();
    }

    /**
     * 将用户领域对象转换为管理端列表项响应。
     * @param user 用户领域对象。
     * @return 管理端用户列表项响应。
     */
    public AdminUserSummaryResponse toAdminSummary(User user) {
        // 步骤 1：列表项只包含管理表格需要展示和筛选的字段。
        // 步骤 2：枚举名称与中文标签同时返回，便于前端既能展示也能保留状态值。
        return new AdminUserSummaryResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getPhone(),
            user.getAvatarUrl(),
            user.getUserType().name(),
            toUserTypeLabel(user),
            user.getStatus().name(),
            toStatusLabel(user),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }

    /**
     * 将用户领域对象转换为管理端详情响应。
     * @param user 用户领域对象。
     * @return 管理端用户详情响应。
     */
    public AdminUserDetailResponse toAdminDetail(User user) {
        // 步骤 1：详情响应比列表多返回最近登录 IP 和更新时间，便于管理端审计。
        // 步骤 2：中文标签与列表保持同一套规则，避免前端或 Controller 分叉。
        return new AdminUserDetailResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getPhone(),
            user.getAvatarUrl(),
            user.getUserType().name(),
            toUserTypeLabel(user),
            user.getStatus().name(),
            toStatusLabel(user),
            user.getLastLoginAt(),
            user.getLastLoginIp(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private String toStatusLabel(User user) {
        return switch (user.getStatus()) {
            case ACTIVE -> "正常";
            case DISABLED -> "禁用";
            case PENDING -> "待审核";
        };
    }

    private String toUserTypeLabel(User user) {
        return switch (user.getUserType()) {
            case ADMIN -> "管理员";
            case USER -> StrUtil.equalsAnyIgnoreCase(user.getStatus().name(), "PENDING") ? "待审核用户" : "普通用户";
        };
    }
}
