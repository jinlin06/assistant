package org.example.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootApplication
public class AssistantApplication {

    private static final Logger log = LoggerFactory.getLogger(AssistantApplication.class);

    public static void main(String[] args) {
        // 本机可能没有 psql / docker 客户端，这里在 Spring/JPA 初始化之前用 JDBC
        // 连到 PostgreSQL 维护库 postgres，若业务库 assistant 不存在则自动创建，
        // 之后 Hibernate ddl-auto=update 负责在该库中建出各模块数据表。
        ensureDatabaseExists();
        SpringApplication.run(AssistantApplication.class, args);
    }

    /** 连接参数与环境变量约定，需与 application.properties 中 spring.datasource.* 保持一致 */
    private static void ensureDatabaseExists() {
        String host = env("PG_HOST", "localhost");
        String port = env("PG_PORT", "5432");
        String dbName = env("PG_DB", "assistant");
        String username = env("PG_USERNAME", "postgres");
        String password = env("PG_PASSWORD", "postgres");

        // 库名只能来自受控的环境变量默认值；如含非法字符则跳过自动建库，交由 DBA 处理
        if (!dbName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            log.warn("库名 {} 含非法标识符，跳过自动建库", dbName);
            return;
        }
        String maintenanceUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection conn = DriverManager.getConnection(maintenanceUrl, username, password)) {
            conn.setAutoCommit(true);
            if (databaseExists(conn, dbName)) {
                log.info("PostgreSQL 数据库已存在：{}", dbName);
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE " + dbName + " ENCODING 'UTF8'");
                log.info("已自动创建 PostgreSQL 数据库：{}", dbName);
            }
        } catch (SQLException e) {
            // 建库失败不阻断启动：若业务库已存在但连不上维护库（权限受限）等场景仍可继续
            log.warn("自动建库检查/创建失败（{}），请确认数据库 {} 是否已存在", e.getMessage(), dbName);
        }
    }

    private static boolean databaseExists(Connection conn, String dbName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
