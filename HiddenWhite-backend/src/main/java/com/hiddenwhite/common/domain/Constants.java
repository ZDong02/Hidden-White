package com.hiddenwhite.common.domain;

import java.time.ZoneId;

/** 跨模块共享常量：集中定义，避免各 Service / Job 各自硬编码同一份值。 */
public final class Constants {

    /** 业务时区：门店经营与订单时间一律以此为准，不随服务器默认时区漂移。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 超级管理员角色标识：拥有全部菜单与权限。 */
    public static final String SUPER_ADMIN = "super_admin";

    private Constants() {
    }
}
