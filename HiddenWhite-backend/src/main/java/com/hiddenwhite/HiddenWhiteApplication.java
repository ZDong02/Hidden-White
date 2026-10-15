package com.hiddenwhite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 隐白咖啡统一后端（单应用启动）：
 * - 小程序 / H5 接口：/api/**（见 com.hiddenwhite.shop）
 * - 管理端接口：/api/admin/**（见 com.hiddenwhite.admin）
 */
@SpringBootApplication
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
