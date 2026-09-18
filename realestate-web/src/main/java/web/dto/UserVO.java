package web.dto;

import java.util.List;

/**
 * 当前登录用户。对应需求报告 G-024。
 *
 * <p>前端拿到它就能决定「显示什么、隐藏什么」——但请记住：界面上的隐藏只是体验，
 * 真正的拦截在后端（core 的 controller 里那道 {@code Session.can(...)}），
 * 这条规矩从 R-001 起就没变过。
 *
 * @param permissions 该角色持有的权限点清单，例如 house:view / house:delete
 */
public record UserVO(String username,
                     String role,
                     String roleName,
                     List<String> permissions) {
}
