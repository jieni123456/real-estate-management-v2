package webtest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import model.User;
import test.TestRunner;
import web.security.JwtUtil;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * JwtUtil 的测试。对应需求报告 G-022。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li><b>正常的签发与解析</b>——用户名、角色能原样取回；并顺带确认 token 的
 *       payload 是「可解的明文」，让人看清它靠签名而非靠保密</li>
 *   <li><b>被改动就必须拒绝</b>——改 payload、改签名、换密钥签发，三种都不该通过</li>
 *   <li><b>非法输入不抛异常</b>——空串、乱码、段数不对，都应安静地返回空，
 *       而不是把异常抛到接口层</li>
 * </ol>
 *
 * <p>密钥通过系统属性注入，测试因此不依赖本机的 db.properties，可在无头机器上跑。
 */
public final class JwtUtilTest {

    /** 测试用密钥：48 个 ASCII 字符即 48 字节，满足「至少 32 字节」的要求 */
    private static final String SECRET = "unit-test-only-secret-key-0123456789abcdefghij";

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtUtilTest() {
    }

    public static void run(TestRunner t) {
        // 密钥走系统属性，优先级高于 db.properties
        System.setProperty("jwt.secret", SECRET);

        try {
            JwtUtil jwtUtil = new JwtUtil();
            runHappyPath(t, jwtUtil);
            runTamperChecks(t, jwtUtil);
            runBadInputChecks(t, jwtUtil);
            runSecretGuardCheck(t);
        } finally {
            // 还原，避免影响本进程内后续的用例
            System.setProperty("jwt.secret", SECRET);
        }
    }

    private static void runHappyPath(TestRunner t, JwtUtil jwtUtil) {
        t.suite("JwtUtil · 签发与解析");

        String token = jwtUtil.issue(User.of("admin", "ADMIN"));

        t.equals("token 恰好三段（header.payload.signature）", 3, token.split("\\.").length);
        t.check("payload 段能被 Base64 解出（说明它只是编码，不是加密）",
                decodePayload(token).contains("\"sub\":\"admin\""));
        t.check("payload 里带着角色", decodePayload(token).contains("\"role\":\"ADMIN\""));
        t.check("payload 里不含口令类字段",
                !decodePayload(token).toLowerCase().contains("password"));

        Optional<User> parsed = jwtUtil.parse(token);
        t.check("签发的 token 能解析成功", parsed.isPresent());
        parsed.ifPresent(user -> {
            t.equals("解析出的用户名", "admin", user.getUsername());
            t.equals("解析出的角色", "ADMIN", user.getRole());
            t.isNull("还原出的用户不带口令哈希", emptyToNull(user.getEncryptedPassword()));
        });

        /* 同一用户两次签发必须不同。
         * 注意：光靠 iat 是不够的 —— 它的精度只到「秒」，同一秒内签发两次会逐字节相同。
         * 真正保证唯一性的是随机 jti，这条断言正是在钉住这个行为
         * （原先这里注释写「带了 iat 所以每次都不一样」，那个前提是错的，
         * 只是此前一直侥幸没在同一秒内连签两次才没暴露）。 */
        t.check("同一用户两次签发的 token 不相同",
                !token.equals(jwtUtil.issue(User.of("admin", "ADMIN"))));
        t.check("payload 里带签发时间 iat", decodePayload(token).contains("\"iat\""));
        t.check("payload 里带唯一票号 jti", decodePayload(token).contains("\"jti\""));
    }

    private static void runTamperChecks(TestRunner t, JwtUtil jwtUtil) {
        t.suite("JwtUtil · 篡改必须被拒绝");

        String token = jwtUtil.issue(User.of("agent", "AGENT"));
        String[] parts = token.split("\\.");

        // 改 payload：把用户冒充成 admin，签名随即对不上
        String forgedPayload = replaceMiddle(parts[1]);
        t.check("改 payload（冒充管理员）后解析失败",
                jwtUtil.parse(parts[0] + "." + forgedPayload + "." + parts[2]).isEmpty());

        // 改签名中间一位：这一位是有效位，改动后解码出的字节确实变了
        String brokenSignature = replaceMiddle(parts[2]);
        t.check("改签名中间一位后解析失败",
                jwtUtil.parse(parts[0] + "." + parts[1] + "." + brokenSignature).isEmpty());

        // 用另一个密钥签发：签名格式合法，但本服务不认
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-for-testing-0123456789".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder()
                .subject("admin").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey).compact();
        t.check("别的密钥签发的 token 不被接受", jwtUtil.parse(foreign).isEmpty());

        // 已过期的 token：用同一个密钥签，但有效期已过
        String expired = Jwts.builder()
                .subject("admin").claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 600_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(KEY).compact();
        t.check("已过期的 token 被拒绝", jwtUtil.parse(expired).isEmpty());
    }

    private static void runBadInputChecks(TestRunner t, JwtUtil jwtUtil) {
        t.suite("JwtUtil · 非法输入安静失败（不抛异常）");

        t.check("null 不抛异常且解析失败", jwtUtil.parse(null).isEmpty());
        t.check("空串不抛异常且解析失败", jwtUtil.parse("").isEmpty());
        t.check("空白串不抛异常且解析失败", jwtUtil.parse("   ").isEmpty());
        t.check("随意字符串不抛异常且解析失败", jwtUtil.parse("not-a-token").isEmpty());
        t.check("只有两段的串被拒绝", jwtUtil.parse("aaa.bbb").isEmpty());
        t.check("三段但内容乱来被拒绝", jwtUtil.parse("aaa.bbb.ccc").isEmpty());
    }

    private static void runSecretGuardCheck(TestRunner t) {
        t.suite("JwtUtil · 密钥缺失时不带默认值启动");

        String original = System.getProperty("jwt.secret");
        System.setProperty("jwt.secret", "too-short");
        try {
            new JwtUtil();
            t.check("过短的密钥应当拒绝构造，但实际没有报错", false);
        } catch (IllegalStateException e) {
            t.check("密钥过短时构造失败，且提示里给出了配置方式",
                    e.getMessage().contains("jwt.secret") && e.getMessage().contains("openssl"));
        } finally {
            System.setProperty("jwt.secret", original == null ? SECRET : original);
        }
    }

    // ------------------------------------------------------------------ 辅助

    /** 把中间那一位换掉（换到别的字符上），用于模拟被改动过的段 */
    private static String replaceMiddle(String segment) {
        int index = segment.length() / 2;
        char original = segment.charAt(index);
        char replacement = original == 'A' ? 'B' : 'A';
        return segment.substring(0, index) + replacement + segment.substring(index + 1);
    }

    private static String decodePayload(String token) {
        String payload = token.split("\\.")[1];
        int padding = (4 - payload.length() % 4) % 4;
        byte[] bytes = Base64.getUrlDecoder().decode(payload + "=".repeat(padding));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
