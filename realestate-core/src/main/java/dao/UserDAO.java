package dao;

import model.User;
import util.DataAccessException;
import util.SecurityUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户表的数据访问。
 *
 * <p>对应需求报告 G-011 / G-012：
 * <ul>
 *   <li>G-011  密码校验改在 Java 侧进行。加盐之后每条记录的哈希都不同，
 *       「WHERE username = ? AND encrypted_password = ?」这种把校验交给 SQL 的写法
 *       已经不可能成立（而且它也无法兼容旧的无盐记录）。</li>
 *   <li>G-012  SQL 异常不再被吞掉，改为抛出已归类的 {@link DataAccessException}。</li>
 * </ul>
 */
public class UserDAO {

    private static final String SELECT_HASH_SQL =
            "SELECT encrypted_password FROM users WHERE username = ?";

    private static final String SELECT_USER_SQL =
            "SELECT username, role FROM users WHERE username = ?";

    private static final String UPDATE_PASSWORD_SQL =
            "UPDATE users SET encrypted_password = ? WHERE username = ?";

    /** 取库中存储的密码哈希。用户不存在返回 null */
    public String findPasswordHash(String username) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_HASH_SQL)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("encrypted_password") : null;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** 按用户名取用户（不含密码）。不存在返回 null */
    public User findByUsername(String username) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_USER_SQL)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // 不把密码带出 DAO：User 的密码字段一律留空
                    return new User(rs.getString("username"), "", rs.getString("role"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /**
     * 写入新密码（明文入参，内部完成加盐哈希）。
     *
     * <p>两个用途：用户主动改密；以及旧格式（无盐）记录在登录成功后自动升级——
     * 后者此时手上正好有明文，所以不必再提供「直接写哈希」的入口。
     *
     * @return 影响行数大于 0 返回 true；用户不存在返回 false
     */
    public boolean updatePassword(String username, String rawNewPassword) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_PASSWORD_SQL)) {

            stmt.setString(1, SecurityUtil.encryptPassword(rawNewPassword));
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
