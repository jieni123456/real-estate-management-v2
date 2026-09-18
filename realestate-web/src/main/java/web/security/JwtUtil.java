package web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import model.User;
import org.springframework.stereotype.Component;
import util.AppConfig;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 的签发与校验。对应需求报告 R-004 / G-022。
 *
 * <p>一个 JWT 就是三段 Base64URL 用点连起来：
 *
 * <pre>
 *   eyJhbGciOiJIUzUxMiJ9 . eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiJ9 . 签名
 *        └ header               └ payload（明文，只是编码，不是加密）      └ 防篡改
 * </pre>
 *
 * <p>要点：payload 只是 Base64 编码、<b>任何人都能解开看</b>，所以里面只放
 * 用户名与角色这类非敏感信息；token 之所以可信，靠的是第三段签名——改了任何一段
 * （payload 或签名本身的有效位），签名就对不上，校验直接失败。
 *
 * <p>签名算法的选择：jjwt 依据密钥长度自动选定——密钥 64 字节时用 HS512
 * （实测 header 为 {@code {"alg":"HS512"}}），32 字节时退到 HS256。
 * 这与「至少 32 字节」的下限是一致的。
 *
 * <p>密钥从配置读取（系统属性 {@code jwt.secret} → 环境变量 {@code JWT_SECRET} →
 * {@code db.properties} 的 {@code jwt.secret}），<b>刻意不留内置默认值</b>：
 * 源码里一旦有默认密钥，等于没有外置——这与 RISK-003 处理联系方式密钥时是同一条规矩。
 */
@Component
public class JwtUtil {

    /** 密钥下限：256 位，即 32 字节。jjwt 会按密钥长度自动选 HS256 / HS384 / HS512 */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String DEFAULT_EXPIRE_MINUTES = "120";

    private final SecretKey key;
    private final long expireMinutes;

    public JwtUtil() {
        String secret = AppConfig.get("jwt.secret", "JWT_SECRET", "jwt.secret");

        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "缺少可用的 JWT 密钥（jwt.secret），无法启动。\n"
                            + "要求：至少 " + MIN_SECRET_BYTES + " 字节（HMAC-SHA 签名的下限；\n"
                            + "      密钥更长时 jjwt 会自动选用更强的算法）。\n"
                            + "任选一种配置方式：\n"
                            + "  1) 写入 db.properties：  jwt.secret=<密钥>\n"
                            + "  2) 设置环境变量：        JWT_SECRET=<密钥>\n"
                            + "  3) 启动参数：            -Djwt.secret=<密钥>\n"
                            + "生成密钥：openssl rand -base64 48");
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = parseExpireMinutes(
                AppConfig.get("jwt.expire-minutes", "JWT_EXPIRE_MINUTES",
                        "jwt.expire-minutes", DEFAULT_EXPIRE_MINUTES));
    }

    /**
     * 签发 token。
     *
     * <p>载荷里只放「谁」和「什么角色」，不放口令、不放联系方式——它是明文可读的。
     */
    public String issue(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMinutes * 60_000L);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解出用户。
     *
     * <p>签名不对、格式不对、已过期——三种情况都返回空 Optional：对调用方而言
     * 结论一样（这个 token 不能用），细分原因反而会泄露信息。
     */
    public Optional<User> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            if (username == null || role == null) {
                return Optional.empty();
            }
            // 用 User.of 而不是 new User(...)：后者会把角色字符串当口令去算哈希，
            // 每个请求都白算一次
            return Optional.of(User.of(username, role));

        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** token 有效期（分钟），登录响应里回给前端，便于它决定何时重新登录 */
    public long expireMinutes() {
        return expireMinutes;
    }

    private long parseExpireMinutes(String value) {
        try {
            long minutes = Long.parseLong(value.trim());
            return minutes > 0 ? minutes : Long.parseLong(DEFAULT_EXPIRE_MINUTES);
        } catch (NumberFormatException e) {
            System.err.println("[配置] jwt.expire-minutes 不是合法数字（" + value
                    + "），改用默认值 " + DEFAULT_EXPIRE_MINUTES);
            return Long.parseLong(DEFAULT_EXPIRE_MINUTES);
        }
    }
}
