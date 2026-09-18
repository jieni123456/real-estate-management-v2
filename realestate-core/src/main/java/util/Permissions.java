package util;

import java.util.Map;
import java.util.Set;

/**
 * 权限点定义与「角色 → 权限」映射。
 *
 * <p>对应需求报告 R-001。设计原则：写操作放开，删除操作收回。
 * AGENT 持有 9 项中的 6 项，三个删除权限（房屋 / 客户 / 带看）被收回。
 */
public final class Permissions {

    // ------------------------------------------------------------ 权限点

    public static final String HOUSE_VIEW = "house:view";
    public static final String HOUSE_ADD = "house:add";
    public static final String HOUSE_DELETE = "house:delete";

    public static final String CUSTOMER_VIEW = "customer:view";
    public static final String CUSTOMER_ADD = "customer:add";
    public static final String CUSTOMER_DELETE = "customer:delete";

    public static final String VIEWING_VIEW = "viewing:view";
    public static final String VIEWING_ADD = "viewing:add";
    public static final String VIEWING_DELETE = "viewing:delete";

    // -------------------------------------------------------------- 角色

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_AGENT = "AGENT";

    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            HOUSE_VIEW, HOUSE_ADD, HOUSE_DELETE,
            CUSTOMER_VIEW, CUSTOMER_ADD, CUSTOMER_DELETE,
            VIEWING_VIEW, VIEWING_ADD, VIEWING_DELETE);

    /** AGENT 可增可查，但不能删除任何数据 */
    private static final Set<String> AGENT_PERMISSIONS = Set.of(
            HOUSE_VIEW, HOUSE_ADD,
            CUSTOMER_VIEW, CUSTOMER_ADD,
            VIEWING_VIEW, VIEWING_ADD);

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            ROLE_ADMIN, ADMIN_PERMISSIONS,
            ROLE_AGENT, AGENT_PERMISSIONS);

    private Permissions() {
    }

    /** 角色对应的权限集合。未知角色返回空集合（默认拒绝，见 Session.can） */
    public static Set<String> of(String role) {
        if (role == null) {
            return Set.of();
        }
        return ROLE_PERMISSIONS.getOrDefault(role.trim().toUpperCase(), Set.of());
    }

    /** 角色字符串是否可识别 */
    public static boolean isKnownRole(String role) {
        return role != null && ROLE_PERMISSIONS.containsKey(role.trim().toUpperCase());
    }

    /** 角色的中文显示名，用于界面展示 */
    public static String displayName(String role) {
        if (role == null) {
            return "未登录";
        }
        switch (role.trim().toUpperCase()) {
            case ROLE_ADMIN:
                return "管理员";
            case ROLE_AGENT:
                return "经纪人";
            default:
                return "未知角色";
        }
    }
}
