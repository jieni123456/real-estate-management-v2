package service;

import dao.UserDAO;
import model.User;
import util.DataAccessException;
import util.SecurityUtil;

/**
 * 登录与密码相关的业务逻辑。
 *
 * <p>对应需求报告 G-011：密码校验搬到 Java 侧完成——加盐之后每条记录的哈希都不同，
 * 无法再用 SQL 的等值匹配来校验。
 */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    /**
     * 校验用户名与密码。
     *
     * @return 通过返回用户对象（不含密码）；用户名不存在或密码错误返回 null
     */
    public User authenticate(String username, String password) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            return null;
        }

        String name = username.trim();
        System.out.println("尝试登录: " + name);

        String storedHash = userDAO.findPasswordHash(name);
        if (storedHash == null || !SecurityUtil.verifyPassword(password, storedHash)) {
            System.out.println("登录失败: 用户名或密码错误");
            return null;
        }

        User user = userDAO.findByUsername(name);
        if (user != null && SecurityUtil.isLegacyHash(storedHash)) {
            upgradeLegacyHash(name, password);
        }
        System.out.println("登录成功: " + name);
        return user;
    }

    /**
     * 把旧格式（无盐）的密码哈希升级为加盐格式。
     *
     * <p>时机选在登录成功这一刻，因为此时手上正好有明文，不必让用户重新设密码，
     * 也不必手工执行 SQL 去改库。
     *
     * <p>升级失败<b>不影响本次登录</b>——下次登录再试一次即可。
     */
    private void upgradeLegacyHash(String username, String plaintextPassword) {
        try {
            if (userDAO.updatePassword(username, plaintextPassword)) {
                System.out.println("已将账号「" + username + "」的密码哈希升级为加盐格式");
            }
        } catch (DataAccessException e) {
            System.err.println("密码哈希升级失败（不影响本次登录）: " + e.getMessage());
        }
    }

    /**
     * 修改密码。
     *
     * @return false 表示原密码不正确或用户不存在；数据库层面的失败会抛
     *         {@link DataAccessException}，由上层区分呈现
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        String storedHash = userDAO.findPasswordHash(username);
        if (storedHash == null || !SecurityUtil.verifyPassword(oldPassword, storedHash)) {
            return false;
        }
        return userDAO.updatePassword(username, newPassword);
    }
}
