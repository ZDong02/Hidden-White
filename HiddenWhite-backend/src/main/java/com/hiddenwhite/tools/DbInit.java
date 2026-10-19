package com.hiddenwhite.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * 本地开发库初始化工具：
 * - 直接执行 database/init.sql（整合版，包含全部业务表 + 系统表 + 种子数据 + 各模块菜单）
 * 连接信息与 application.yml 使用同一组环境变量（DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD）。
 * 传入参数 fix 时先删除并重建数据库。
 * 运行方式（项目根目录）：
 *   mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit
 *
 * <p><b>危险操作</b>：带 {@code fix} 参数会执行 {@code DROP DATABASE}，
 * 目标库内的全部数据不可恢复。仅用于本地开发库，严禁对生产或共享库执行。
 * 进度信息走 stdout 而非 logger，是命令行工具的常规做法。
 */
public class DbInit {

    public static void main(String[] args) throws Exception {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        String db = env("DB_NAME", "hidden_white");
        String user = env("DB_USERNAME", "root");
        String pass = env("DB_PASSWORD", "123456");
        if (!db.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("DB_NAME 只能包含字母、数字和下划线");
        String zone = env("DB_TIMEZONE", "+08:00");
        String params = "useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true";
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", pass);
        properties.setProperty("connectionTimeZone", zone);
        properties.setProperty("forceConnectionTimeZoneToSession", "true");
        boolean fix = args.length > 0 && "fix".equals(args[0]);

        try (Connection c = DriverManager.getConnection(String.format("jdbc:mysql://%s:%s/?%s", host, port, params), properties);
             Statement s = c.createStatement()) {
            if (fix) {
                System.out.println("删除并重建数据库: " + db);
                s.execute("DROP DATABASE IF EXISTS `" + db + "`");
            }
            s.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci");
        }

        Path initScript = Path.of("database", "init.sql");
        if (!Files.exists(initScript)) {
            throw new IllegalStateException("找不到数据库初始化脚本: " + initScript.toAbsolutePath());
        }

        try (Connection c = DriverManager.getConnection(String.format("jdbc:mysql://%s:%s/%s?%s", host, port, db, params), properties);
             Statement s = c.createStatement()) {
            System.out.println("导入: " + initScript);
            s.execute(Files.readString(initScript));
        }
        System.out.println("数据库初始化完成");
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
