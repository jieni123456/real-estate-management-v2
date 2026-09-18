package test;

import model.User;
import util.Permissions;
import util.Session;

/**
 * Permissions 与 Session 的测试。对应需求报告 G-016（及 R-001 的权限设计）。
 *
 * <p>权限判断是纯函数，最适合单测。这里除了覆盖「ADMIN 全有、AGENT 缺两个删除」
 * 这条业务规则，还专门验证 fail-safe 底线：<b>未登录或角色无法识别时一律不给权限</b>。
 * 这类「默认拒绝」的逻辑一旦写反，就是很严重的安全问题，只有测试能守住。
 */
public final class PermissionsTest {

    private PermissionsTest() {
    }

    public static void run(TestRunner t) {
        t.suite("Permissions · 角色与权限映射");

        t.equals("ADMIN 权限数量", 9, Permissions.of(Permissions.ROLE_ADMIN).size());
        t.equals("AGENT 权限数量", 6, Permissions.of(Permissions.ROLE_AGENT).size());

        t.check("ADMIN 有删除房屋权限",
                Permissions.of("ADMIN").contains(Permissions.HOUSE_DELETE));
        t.check("ADMIN 有删除客户权限",
                Permissions.of("ADMIN").contains(Permissions.CUSTOMER_DELETE));
        t.check("ADMIN 有删除带看权限",
                Permissions.of("ADMIN").contains(Permissions.VIEWING_DELETE));
        t.check("AGENT 无删除房屋权限",
                !Permissions.of("AGENT").contains(Permissions.HOUSE_DELETE));
        t.check("AGENT 无删除客户权限",
                !Permissions.of("AGENT").contains(Permissions.CUSTOMER_DELETE));
        t.check("AGENT 无删除带看权限",
                !Permissions.of("AGENT").contains(Permissions.VIEWING_DELETE));
        t.check("AGENT 有新增房屋权限",
                Permissions.of("AGENT").contains(Permissions.HOUSE_ADD));
        t.check("AGENT 有新增客户权限",
                Permissions.of("AGENT").contains(Permissions.CUSTOMER_ADD));
        t.check("AGENT 有登记带看权限",
                Permissions.of("AGENT").contains(Permissions.VIEWING_ADD));
        t.check("AGENT 有查看房屋权限",
                Permissions.of("AGENT").contains(Permissions.HOUSE_VIEW));
        t.check("AGENT 有查看带看权限",
                Permissions.of("AGENT").contains(Permissions.VIEWING_VIEW));

        t.check("角色名大小写不敏感", Permissions.of("admin").contains(Permissions.HOUSE_DELETE));
        t.check("角色名首尾空格被忽略",
                Permissions.of("  AGENT ").contains(Permissions.HOUSE_ADD));
        t.equals("未知角色权限为空", 0, Permissions.of("SUPER").size());
        t.equals("null 角色权限为空", 0, Permissions.of(null).size());
        t.check("isKnownRole 识别 ADMIN", Permissions.isKnownRole("admin"));
        t.check("isKnownRole 拒绝未知角色", !Permissions.isKnownRole("root"));
        t.check("isKnownRole 对 null 返回 false", !Permissions.isKnownRole(null));

        t.equals("角色显示名 · ADMIN", "管理员", Permissions.displayName("ADMIN"));
        t.equals("角色显示名 · AGENT", "经纪人", Permissions.displayName("agent"));
        t.equals("角色显示名 · 未知", "未知角色", Permissions.displayName("x"));
        t.equals("角色显示名 · null", "未登录", Permissions.displayName(null));

        t.suite("Session · 权限判断的默认拒绝（fail-safe）");

        Session.logout();
        t.check("未登录时任何权限都为 false", !Session.can(Permissions.HOUSE_VIEW));
        t.check("未登录时不算已登录", !Session.isLoggedIn());
        t.equals("未登录时的顶栏文案", "未登录", Session.currentUserLabel());

        Session.login(new User("admin", "", Permissions.ROLE_ADMIN));
        t.check("ADMIN 登录后可删除房屋", Session.can(Permissions.HOUSE_DELETE));
        t.equals("ADMIN 的顶栏文案", "admin · 管理员", Session.currentUserLabel());

        Session.logout();
        Session.login(new User("agent", "", Permissions.ROLE_AGENT));
        t.check("AGENT 登录后不能删除房屋", !Session.can(Permissions.HOUSE_DELETE));
        t.check("AGENT 登录后可以新增房屋", Session.can(Permissions.HOUSE_ADD));
        t.check("AGENT 登录后可以登记带看", Session.can(Permissions.VIEWING_ADD));
        t.check("AGENT 登录后不能删除带看", !Session.can(Permissions.VIEWING_DELETE));
        t.equals("AGENT 的顶栏文案", "agent · 经纪人", Session.currentUserLabel());

        Session.logout();
        Session.login(new User("hacker", "", "SUPER"));
        t.check("角色无法识别时即便已登录也不给任何权限",
                !Session.can(Permissions.HOUSE_VIEW));
        t.equals("未知角色的显示名", "hacker · 未知角色", Session.currentUserLabel());

        Session.logout();
        Session.login(new User("admin", "", Permissions.ROLE_ADMIN));
        t.check("权限点为 null 时返回 false", !Session.can(null));

        // 收尾：Session 是全局状态，测试结束后必须清空，否则会影响其它测试
        Session.logout();
        t.check("退出登录后 Session 已清空", !Session.isLoggedIn());
    }
}
