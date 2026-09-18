package web.dto;

/**
 * 登录成功后返回给前端的内容。对应需求报告 G-024。
 *
 * <p>把角色与有效期一并回给前端：前端据此决定显示哪些菜单、以及何时提示重新登录。
 * 注意这里<b>不含</b>口令哈希等任何敏感字段。
 *
 * @param token            JWT，后续请求放在 {@code Authorization: Bearer <token>} 里
 * @param tokenType        固定为 Bearer，便于前端拼请求头
 * @param expiresInMinutes 有效期（分钟）
 * @param username         用户名
 * @param role             角色代码（ADMIN / AGENT）
 * @param roleName         角色的中文显示名
 */
public record LoginResponse(String token,
                            String tokenType,
                            long expiresInMinutes,
                            String username,
                            String role,
                            String roleName) {
}
