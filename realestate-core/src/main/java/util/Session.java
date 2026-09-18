package util;

import model.User;

/**
 * 会话：保存「当前登录用户」并提供权限判断。
 *
 * <p>对应需求报告：
 * <ul>
 *   <li>R-001  权限判断的唯一入口：{@link #can(String)}。</li>
 *   <li>G-022  当前用户的存放方式改为可替换策略（{@link UserHolder}）——
 *       桌面端用静态字段（同时只有一个用户），Web 端换成 ThreadLocal
 *       （并发请求各自的用户不能互相覆盖）。调用点因此一行都不用改。</li>
 * </ul>
 *
 * <p>安全底线：{@link #can} 在「未登录」或「角色无法识别」时一律返回 false
 * （fail-safe，宁可少给权限，不可错给权限）。
 */
public final class Session {

    /**
     * 当前用户的存放策略。
     *
     * <p>默认是桌面端用的静态字段实现；Web 端在启动时通过 {@link #setHolder} 换成
     * ThreadLocal 实现。用 volatile 是因为替换可能发生在其它线程的初始化阶段。
     */
    private static volatile UserHolder holder = new StaticHolder();

    private Session() {
    }

    /**
     * 替换「当前用户存在哪」的策略。由 Web 端在启动时调用一次。
     *
     * <p>桌面端不调用，保持默认实现，行为与改造前完全一致。
     *
     * @param newHolder 传 null 表示恢复默认实现
     */
    public static void setHolder(UserHolder newHolder) {
        holder = newHolder == null ? new StaticHolder() : newHolder;
    }

    /** 登录成功后由 AuthController 调用 */
    public static void login(User user) {
        holder.set(user);
    }

    /** 退出登录时由 AuthController 调用，务必清空，否则会残留上一次的权限状态 */
    public static void logout() {
        holder.clear();
    }

    public static User currentUser() {
        return holder.get();
    }

    public static boolean isLoggedIn() {
        return holder.get() != null;
    }

    public static String currentUsername() {
        User user = holder.get();
        return user == null ? "未登录" : user.getUsername();
    }

    public static String currentRole() {
        User user = holder.get();
        return user == null ? null : user.getRole();
    }

    /** 当前用户角色的中文显示名 */
    public static String currentRoleName() {
        return Permissions.displayName(currentRole());
    }

    /** 顶栏展示用，例如「admin · 管理员」 */
    public static String currentUserLabel() {
        User user = holder.get();
        if (user == null) {
            return "未登录";
        }
        return user.getUsername() + " · " + Permissions.displayName(user.getRole());
    }

    /**
     * 当前用户是否具备指定权限。
     * 未登录、权限点为 null、或角色无法识别时均返回 false。
     */
    public static boolean can(String permission) {
        User user = holder.get();
        if (user == null || permission == null) {
            return false;
        }
        return Permissions.of(user.getRole()).contains(permission);
    }

    /**
     * 桌面端的默认实现：一个静态字段。
     *
     * <p>之所以可以这么简单——单窗口桌面程序同一时刻只有一个登录用户，
     * 不存在并发覆盖的问题。
     */
    private static final class StaticHolder implements UserHolder {

        private User currentUser;

        @Override
        public User get() {
            return currentUser;
        }

        @Override
        public void set(User user) {
            currentUser = user;
        }

        @Override
        public void clear() {
            currentUser = null;
        }
    }
}
