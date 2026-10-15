package com.hiddenwhite.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * 本地开发库初始化工具：
 * - 导入 database/hidden_white_mysql.sql（咖啡业务表：小程序端 + 管理端共用）
 * - 按版本号顺序导入 database/admin/V*__*.sql（管理端系统表 + 种子数据 + 各模块菜单）
 *   V1 系统表 / V2 种子数据 / V3 咖啡运营菜单 / V4 门店运营表+菜单
 * - 也可直接执行 database/init.sql 整合版一键初始化
 * 连接信息与 application.yml 使用同一组环境变量（DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD）。
 * 传入参数 fix 时先删除并重建数据库。
 * 运行方式（项目根目录）：
 *   mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit
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

        Path root = Path.of("database");
        List<Path> files;
        try (Stream<Path> stream = Files.list(root.resolve("admin"))) {
            List<Path> adminScripts = stream.filter(f -> f.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(Comparator.comparingInt(DbInit::version))
                    .toList();
            files = Stream.concat(Stream.of(root.resolve("hidden_white_mysql.sql")), adminScripts.stream()).toList();
        }

        try (Connection c = DriverManager.getConnection(String.format("jdbc:mysql://%s:%s/%s?%s", host, port, db, params), properties);
             Statement s = c.createStatement()) {
            for (Path f : files) {
                System.out.println("导入: " + f);
                s.execute(Files.readString(f));
            }
        }
        System.out.println("数据库初始化完成");
    }

    private static int version(Path file) {
        String name = file.getFileName().toString();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
