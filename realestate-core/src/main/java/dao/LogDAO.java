package dao;

import model.OperationLog;
import util.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志的数据访问。对应需求报告 G-017。
 *
 * <p>表由 {@code DatabaseUtil.initializeDatabase()} 以 CREATE TABLE IF NOT EXISTS
 * 建立，因此不需要额外的手工建表脚本。
 *
 * <p>时间列用数据库的 CURRENT_TIMESTAMP 默认值写入（取数据库服务器的时间，
 * 比客户端时间更可信），读取时在 SQL 侧格式化成「月-日 时:分」，界面直接用。
 */
public class LogDAO {

    private static final String INSERT_SQL =
            "INSERT INTO operation_logs (operator, role, action, target, detail) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_RECENT_SQL =
            "SELECT DATE_FORMAT(created_at, '%m-%d %H:%i') AS happened_at, "
                    + "operator, role, action, target, detail "
                    + "FROM operation_logs ORDER BY id DESC LIMIT ?";

    public boolean insert(OperationLog log) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {

            stmt.setString(1, nullToEmpty(log.getOperator()));
            stmt.setString(2, nullToEmpty(log.getRole()));
            stmt.setString(3, nullToEmpty(log.getAction()));
            stmt.setString(4, nullToEmpty(log.getTarget()));
            stmt.setString(5, nullToEmpty(log.getDetail()));
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** 最近若干条，时间倒序 */
    public List<OperationLog> findRecent(int limit) {
        List<OperationLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_RECENT_SQL)) {

            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(new OperationLog(
                            rs.getString("happened_at"),
                            rs.getString("operator"),
                            rs.getString("role"),
                            rs.getString("action"),
                            rs.getString("target"),
                            rs.getString("detail")));
                }
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
        return logs;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
