package test;

import util.SecurityUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SecurityUtil 的测试。对应需求报告 G-016（密码加盐 G-011、加密改造 RISK-003）。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>加盐哈希的行为——同密码两次结果不同、错误密码不通过、坏数据不抛异常</li>
 *   <li><b>对旧密码数据的兼容</b>——改造前写入的无盐哈希必须仍能校验通过，
 *       否则用户改完代码就登不进系统了</li>
 *   <li><b>联系方式加密</b>——GCM 往返正确、相同明文密文不同、密文被改动要能被
 *       发现，以及旧 ECB 密文不再能解开（它需要一次性迁移）</li>
 * </ol>
 */
public final class SecurityUtilTest {

    /**
     * 改造前数据库里 admin 账号的哈希值（无盐 SHA-256 后 Base64）。
     * 它是真实存在过的值，用它来验证兼容性比造一个假值更有意义。
     */
    private static final String LEGACY_ADMIN_HASH =
            "JAvlGPq9JyTdtvBO6x2llnRI1+gxwIyPqCKAn3THIKk=";

    /**
     * 本组测试用的固定密钥（Base64 的 16 字节）。
     * 单元测试不该依赖本机的 db.properties，因此用系统属性注入。
     */
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    /** 合法的 Base64、但不含分隔符——即改造前 ECB 密文的形态 */
    private static final String LEGACY_CONTACT_SAMPLE = "AAAAAAAAAAAAAAAAAAAAAA==";

    private SecurityUtilTest() {
    }

    public static void run(TestRunner t) {
        // 密钥走系统属性，测试因此不依赖本机配置文件
        System.setProperty("contact.key", TEST_KEY);

        t.suite("SecurityUtil · 密码加盐（G-011）");

        String hash = SecurityUtil.encryptPassword("admin123");
        t.check("加盐后格式为 salt:hash", hash.indexOf(':') > 0);
        t.check("同一密码两次哈希不相同（盐是随机的）",
                !hash.equals(SecurityUtil.encryptPassword("admin123")));
        t.check("正确密码校验通过", SecurityUtil.verifyPassword("admin123", hash));
        t.check("错误密码校验不通过", !SecurityUtil.verifyPassword("admin124", hash));
        t.check("大小写敏感", !SecurityUtil.verifyPassword("Admin123", hash));
        t.check("空密码不通过", !SecurityUtil.verifyPassword("", hash));
        t.check("存储值为 null 不通过", !SecurityUtil.verifyPassword("admin123", null));
        t.check("存储值为空串不通过", !SecurityUtil.verifyPassword("admin123", ""));
        t.check("存储值格式损坏时不抛异常且不通过",
                !SecurityUtil.verifyPassword("admin123", "不是Base64:也是坏的"));
        t.check("新格式不被判定为旧格式", !SecurityUtil.isLegacyHash(hash));
        t.check("isLegacyHash 对 null 返回 false", !SecurityUtil.isLegacyHash(null));

        t.suite("SecurityUtil · 兼容改造前的无盐哈希");

        t.check("旧格式哈希仍能校验通过（否则老账号会登不进）",
                SecurityUtil.verifyPassword("admin123", LEGACY_ADMIN_HASH));
        t.check("旧格式哈希被识别为待升级",
                SecurityUtil.isLegacyHash(LEGACY_ADMIN_HASH));
        t.check("旧格式下错误密码依然不通过",
                !SecurityUtil.verifyPassword("wrong", LEGACY_ADMIN_HASH));

        t.suite("SecurityUtil · 房东联系方式加解密（RISK-003：GCM）");

        String encrypted = SecurityUtil.encryptContact("13800138000");
        String again = SecurityUtil.encryptContact("13800138000");

        t.check("加密结果与明文不同", !encrypted.equals("13800138000"));
        t.equals("解密可还原", "13800138000", SecurityUtil.decryptContact(encrypted));
        t.equals("中文与符号也能往返",
                "李四 138-0013",
                SecurityUtil.decryptContact(SecurityUtil.encryptContact("李四 138-0013")));

        t.check("存储格式为 IV:密文（含分隔符）", encrypted.indexOf(':') > 0);
        t.equals("IV 段解码后为 12 字节", 12, Base64.getDecoder().decode(ivOf(encrypted)).length);

        t.check("相同明文两次密文不同——GCM 随机 IV，从库里看不出两个电话是否相同",
                !encrypted.equals(again));
        t.check("两次的 IV 段也不同", !ivOf(encrypted).equals(ivOf(again)));

        t.check("密文段被改动一个字节即解密失败（GCM 认证标签）",
                throwsIllegalState(() -> SecurityUtil.decryptContact(tamperCiphertext(encrypted))));
        t.check("IV 段被改动即解密失败",
                throwsIllegalState(() -> SecurityUtil.decryptContact(tamperIv(encrypted))));
        t.check("把别人的密文接到本条的 IV 上会失败（IV 与密文不匹配）",
                throwsIllegalState(() -> SecurityUtil.decryptContact(
                        ivOf(encrypted) + ":" + ciphertextOf(again))));

        t.check("空串往返为空串，而不是抛异常",
                SecurityUtil.decryptContact(SecurityUtil.encryptContact("")).isEmpty());
        t.isNull("加密 null 返回 null", SecurityUtil.encryptContact(null));

        t.suite("SecurityUtil · 旧 ECB 密文的识别与拦截");

        t.check("不含分隔符的值被识别为旧格式",
                SecurityUtil.isLegacyContact(LEGACY_CONTACT_SAMPLE));
        t.check("新格式不被误判为旧格式", !SecurityUtil.isLegacyContact(encrypted));
        t.check("isLegacyContact 对 null 返回 false", !SecurityUtil.isLegacyContact(null));
        t.check("直接解密旧格式会抛出异常（提示需要迁移），而不是解出乱码",
                throwsIllegalState(() -> SecurityUtil.decryptContact(LEGACY_CONTACT_SAMPLE)));
    }

    // ---------------------------------------------------------------- 辅助

    /** 断言这段代码会抛出 IllegalStateException */
    private static boolean throwsIllegalState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** IV 段的 Base64 文本 */
    private static String ivOf(String sealed) {
        return sealed.substring(0, sealed.indexOf(':'));
    }

    /** 密文段的 Base64 文本 */
    private static String ciphertextOf(String sealed) {
        return sealed.substring(sealed.indexOf(':') + 1);
    }

    /** 改动密文段的一个字节，用于验证 GCM 的完整性校验 */
    private static String tamperCiphertext(String sealed) {
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextOf(sealed));
        ciphertext[0] ^= 0x01;
        return ivOf(sealed) + ":" + Base64.getEncoder().encodeToString(ciphertext);
    }

    /** 改动 IV 段的一个字节 */
    private static String tamperIv(String sealed) {
        byte[] iv = Base64.getDecoder().decode(ivOf(sealed));
        iv[0] ^= 0x01;
        return Base64.getEncoder().encodeToString(iv) + ":" + ciphertextOf(sealed);
    }
}
