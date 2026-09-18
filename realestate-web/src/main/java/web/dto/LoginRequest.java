package web.dto;

/**
 * 登录请求体。对应需求报告 G-024。
 *
 * <p>用 record 而不是让 core 的 {@code User} 充当入参：{@code User} 的构造函数
 * 接收明文口令并立刻算哈希，把它当传输对象会让「传输」和「存储」两件事混在一起。
 *
 * @param username 用户名
 * @param password 明文口令（仅在本次请求的内存中存在，不落任何日志）
 */
public record LoginRequest(String username, String password) {
}
