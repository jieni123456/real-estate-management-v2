package controller;

import model.User;
import service.AuthService;
import service.LogService;
import util.DataAccessException;
import util.Permissions;
import util.Result;
import util.Session;
import util.Validators;

/**
 * 登录、退出与密码修改。
 *
 * <p>对应需求报告：
 * <ul>
 *   <li>R-001  登录成功后把用户写入全局 {@link Session}，退出时清空——
 *       务必清空，否则再次登录会残留上一次的权限状态。</li>
 *   <li>G-015  提供修改密码入口，校验原密码、新密码规则与两次输入一致。</li>
 *   <li>G-017  登录成功 / 失败、修改密码均写操作日志。</li>
 * </ul>
 */
public class AuthController {

    private final AuthService authService = new AuthService();
    private final LogService logService = new LogService();

    /**
     * @return 登录成功返回用户对象，失败返回 null
     */
    public User login(String username, String password) {
        User user = authService.authenticate(username, password);

        if (user == null) {
            // 失败尝试同样留痕：这既是排查依据，也是发现异常登录的线索。
            // 此刻 Session 里还没有用户，只能以「尝试登录的用户名」作为操作者。
            logService.recordAs(username, "", "登录失败", username, "用户名或密码错误");
            return null;
        }

        Session.login(user);
        logService.record("登录成功", user.getUsername(),
                "角色 " + Permissions.displayName(user.getRole()));
        return user;
    }

    public void logout() {
        // 先记日志再清 Session——顺序反了就拿不到操作者了
        if (Session.isLoggedIn()) {
            logService.record("退出登录", Session.currentUsername(), "");
        }
        Session.logout();
    }

    /** 当前登录用户。以 Session 为唯一事实来源，避免两处状态不一致 */
    public User getCurrentUser() {
        return Session.currentUser();
    }

    /**
     * 修改当前登录用户的密码。对应需求报告 G-015。
     *
     * <p>只改自己的密码，不允许改他人——因此不需要额外权限，
     * 两个角色都可以用。
     */
    public Result changePassword(String oldPassword, String newPassword, String confirmPassword) {
        User current = Session.currentUser();
        if (current == null) {
            return Result.fail("尚未登录，无法修改密码。");
        }

        String error = Validators.requiredText("原密码", oldPassword, 64);
        if (error != null) {
            return Result.fail(error);
        }
        error = Validators.password("新密码", newPassword);
        if (error != null) {
            return Result.fail(error);
        }
        if (!newPassword.equals(confirmPassword)) {
            return Result.fail("两次输入的新密码不一致");
        }
        if (newPassword.equals(oldPassword)) {
            return Result.fail("新密码不能与原密码相同");
        }

        try {
            if (!authService.changePassword(current.getUsername(), oldPassword, newPassword)) {
                return Result.fail("原密码不正确");
            }
        } catch (DataAccessException e) {
            return Result.fail(e.userMessage());
        }

        // 刻意不记录任何密码内容，只记录「谁改了密码」
        logService.record("修改密码", current.getUsername(), "");
        return Result.ok("密码修改成功，下次登录请使用新密码");
    }
}
