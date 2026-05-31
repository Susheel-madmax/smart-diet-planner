package com.diet.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database connection utility for Oracle SQL
 * Update URL, USERNAME, PASSWORD before running
 */
public class DBConnection {

    // ── UPDATE THESE ──────────────────────────────────────────────────────────
    private static final String URL = "jdbc:oracle:thin:@localhost:1521:XE";

    private static final String USERNAME = "username";

    private static final String PASSWORD = "userpassword";
    // ─────────────────────────────────────────────────────────────────────────

    static {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Oracle JDBC Driver not found!", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }
}
