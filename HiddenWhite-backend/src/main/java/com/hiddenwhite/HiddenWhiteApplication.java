package com.hiddenwhite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 隐白咖啡统一后端（单应用启动）：
 * - 小程序 / H5 接口：/api/**（见 com.hiddenwhite.shop）
 * - 管理端接口：/api/admin/**（见 com.hiddenwhite.admin）
 */
@SpringBootApplication
// 启用定时任务：订单超时取消与日客流结算依赖它。改造前全项目零 @Scheduled，
// 导致超时订单永久阻塞会员再次下单、跨日客流永不结算
@EnableScheduling
@MapperScan({
        "com.hiddenwhite.admin.system.mapper",
        "com.hiddenwhite.admin.generator.mapper",
        "com.hiddenwhite.admin.coffee.mapper",
        "com.hiddenwhite.admin.operation.mapper"
})
public class HiddenWhiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(HiddenWhiteApplication.class, args);
    }
}
