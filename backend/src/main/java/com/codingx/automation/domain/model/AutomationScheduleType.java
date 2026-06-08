package com.codingx.automation.domain.model;

/**
 * 自动化任务计划类型，第一版仅覆盖参考图需要的每日、每周和一次性计划。
 */
public enum AutomationScheduleType {
    /** 每天在固定时间执行。 */
    DAILY,
    /** 每周在指定星期和固定时间执行。 */
    WEEKLY,
    /** 在指定日期时间执行一次。 */
    ONCE
}
