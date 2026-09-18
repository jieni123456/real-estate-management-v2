package util;

import model.User;

/**
 * 全局会话：保存当前登录用户并提供权限判断。
 *
 * <p>采用静态字段而非层层传参，理由见需求报告 R-001 决策点 3：
 * 本程序是单窗口桌面应用，同一时刻只有一个登录用户，全局状态可接受，
 * 且不必改动任何 Controller / Service 的方法签名。
 *
 * <p>安全底线：{@link #can} 在「未登录」或「角色无法识别」时一律返回 false
 * （fail-safe，宁可少给权限，不可错给权限）。
 */
public final class Session {

    private static User currentUser;

    private Session() {
    }

    /** 登录成功后由 AuthController 调用 */
    public static void login(User user) {
        currentUser = user;
    }

    /** 退出登录时由 AuthController 调用，务必清空，否则会残留上一次的权限状态 */
    public static void logout() {
        currentUser = null;
    }

    public static User currentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static String currentUsername() {
        return currentUser == null ? "未登录" : currentUser.getUsername();
    }

    public static String currentRole() {
        return currentUser == null ? null : currentUser.getRole();
    }

    /** 当前用户角色的中文显示名 */
    public static String currentRoleName() {
        return Permissions.displayName(currentRole());
    }

    /** 顶栏展示用，例如「admin · 管理员」 */
    public static String currentUserLabel() {
        if (currentUser == null) {
            return "未登录";
        }
        return currentUser.getUsername() + " · " + Permissions.displayName(currentUser.getRole());
    }

    /**
     * 当前用户是否具备指定权限。
     * 未登录、权限点为 null、或角色无法识别时均返回 false。
     */
    public static boolean can(String permission) {
        if (currentUser == null || permission == null) {
            return false;
        }
        return Permissions.of(currentUser.getRole()).contains(permission);
    }
}
