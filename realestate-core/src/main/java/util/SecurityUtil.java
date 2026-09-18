package util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希与敏感字段加解密。
 *
 * <p>对应需求报告：
 * <ul>
 *   <li><b>G-011</b>  密码由「SHA-256(密码) 直接 Base64」改为
 *       <b>「每用户独立随机盐 + SHA-256」</b>，存储格式 {@code salt:hash}
 *       （两段均为 Base64）。加盐之后相同密码在不同账号上的哈希也不同，
 *       彩虹表无法直接反查。</li>
 *   <li><b>RISK-003</b>  房东联系方式由 {@code AES/ECB} 改为 <b>{@code AES/GCM}</b>：
 *       每次加密使用新的随机 IV（相同明文密文不再相同），并带 128 位认证标签
 *       （密文被改动会被检测出来）。存储格式 {@code Base64(IV):Base64(密文+标签)}。
 *       密钥不再硬编码在源码里，改为从配置读取，见 {@link #resolveContactKey}。</li>
 * </ul>
 *
 * <p><b>密码的旧数据兼容</b>：历史记录是无盐哈希（不含分隔符）。{@link #verifyPassword}
 * 仍能校验通过，{@link #isLegacyHash} 用于识别，登录成功后由 AuthService 顺手把它
 * 升级为加盐格式——不必手工改库，也不会让已有账号登不进来。
 *
 * <p><b>联系方式的旧数据不兼容</b>：历史记录是 ECB 密文（同样不含分隔符），
 * 用新密钥解不开。需要一次性数据迁移把库里旧密文重写为 GCM 格式；
 * {@link #isLegacyContact} 供迁移程序识别待转换的记录。
 */
public final class SecurityUtil {

    /**
     * 加盐哈希与 GCM 密文都用它分隔前后两段。
     * Base64 字符集不含冒号，因此用它做分隔是安全的（两种密文分属不同字段，互不混淆）。
     */
    private static final char SEPARATOR = ':';
    private static final int SALT_BYTES = 16;

    // ------------------------------------------------ 联系方式密钥的配置来源

    private static final String KEY_SYSTEM_PROPERTY = "contact.key";
    private static final String KEY_ENV_VARIABLE = "CONTACT_KEY";
    private static final String KEY_FILE_KEY = "contact.key";

    private static final String CONTACT_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 联系方式加密密钥。刻意做成「首次使用时才解析」而不是静态常量：
     * 测试可以先指定 {@code -Dcontact.key}，再触发解析。
     */
    private static volatile SecretKeySpec contactKey;

    private SecurityUtil() {
    }

    // -------------------------------------------------------------- 密码

    /**
     * 生成带随机盐的密码哈希，格式 {@code salt:hash}。
     * 每次调用结果都不同（盐是随机的），这是加盐的预期行为。
     */
    public static String encryptPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + SEPARATOR + hashWithSalt(password, salt);
    }

    /**
     * 校验密码。
     *
     * @param rawPassword 用户输入的明文
     * @param stored      库中存储的值——加盐格式 {@code salt:hash} 或旧的无盐 Base64
     * @return 匹配返回 true；任一参数为空、格式非法、值不匹配均返回 false
     */
    public static boolean verifyPassword(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isEmpty()) {
            return false;
        }

        int index = stored.indexOf(SEPARATOR);
        if (index < 0) {
            // 旧格式：无盐值。为兼容历史数据保留，登录后会被自动升级
            return constantTimeEquals(legacyHash(rawPassword), stored);
        }

        try {
            byte[] salt = Base64.getDecoder().decode(stored.substring(0, index));
            String expected = stored.substring(index + 1);
            return constantTimeEquals(hashWithSalt(rawPassword, salt), expected);
        } catch (IllegalArgumentException e) {
            // 存储值被改坏或格式非法：一律视为不匹配，不抛异常
            return false;
        }
    }

    /** 是否为无盐值的旧格式哈希（登录成功后应顺手升级） */
    public static boolean isLegacyHash(String stored) {
        return stored != null && !stored.isEmpty() && stored.indexOf(SEPARATOR) < 0;
    }

    private static String hashWithSalt(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    /** 旧格式（无盐）的哈希，仅供兼容校验使用 */
    private static String legacyHash(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    /** 定时安全比较，避免按字符提前返回泄露信息。用 MessageDigest.isEqual 而非 equals */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------ 房东联系方式

    /**
     * 加密联系方式。AES-GCM，每次使用新的随机 IV。
     *
     * <p>输出 {@code Base64(IV):Base64(密文+认证标签)}。相比原来的 ECB：
     * 相同明文每次密文都不同（从库里看不出两个房东电话是否相同），
     * 密文被改动也会在解密时被发现，而不是解出一段乱码。
     */
    public static String encryptContact(String contact) {
        if (contact == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CONTACT_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, contactKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] sealed = cipher.doFinal(contact.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(sealed);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("联系方式加密失败", e);
        }
    }

    /**
     * 解密联系方式。
     *
     * @throws IllegalStateException 遇到旧格式（ECB）密文，或密文被改动、密钥不匹配
     */
    public static String decryptContact(String encryptedContact) {
        if (encryptedContact == null || encryptedContact.isEmpty()) {
            return "";
        }

        int index = encryptedContact.indexOf(SEPARATOR);
        if (index < 0) {
            // 改造前是 AES/ECB 且无 IV，存的是单个 Base64 串，用当前密钥解不开
            throw new IllegalStateException("该联系方式仍是改造前的 ECB 密文，"
                    + "无法用当前密钥解开；请先执行 RISK-003 的一次性数据迁移。");
        }

        try {
            byte[] iv = Base64.getDecoder().decode(encryptedContact.substring(0, index));
            byte[] sealed = Base64.getDecoder().decode(encryptedContact.substring(index + 1));

            Cipher cipher = Cipher.getInstance(CONTACT_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, contactKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // GCM 的认证标签校验失败也走这里——密文被改动时不会解出错误的明文
            throw new IllegalStateException("联系方式解密失败（数据被改动或密钥不匹配）", e);
        }
    }

    /** 是否为改造前的 ECB 密文（无 IV）。数据迁移用它识别待转换的记录 */
    public static boolean isLegacyContact(String stored) {
        return stored != null && !stored.isEmpty() && stored.indexOf(SEPARATOR) < 0;
    }

    // ---------------------------------------------------------- 密钥解析

    /** 解析并缓存联系方式密钥（RISK-003）。首次使用时调用 */
    private static SecretKeySpec contactKey() {
        SecretKeySpec cached = contactKey;
        if (cached == null) {
            synchronized (SecurityUtil.class) {
                if (contactKey == null) {
                    contactKey = resolveContactKey();
                }
                cached = contactKey;
            }
        }
        return cached;
    }

    /**
     * 按优先级取密钥：{@code -Dcontact.key} → 环境变量 {@code CONTACT_KEY} →
     * {@code db.properties} 的 {@code contact.key}。
     *
     * <p>刻意<b>不</b>留内置默认值：源码里一旦有默认密钥，等于没有外置。
     * 取不到时给出可直接照做的修复指引，而不是抛一条看不懂的异常。
     */
    private static SecretKeySpec resolveContactKey() {
        String value = AppConfig.get(KEY_SYSTEM_PROPERTY, KEY_ENV_VARIABLE, KEY_FILE_KEY);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "未配置房东联系方式的加密密钥。任选一种方式配置：\n"
                            + "  1) 在 db.properties 中加一行  contact.key=<Base64 密钥>\n"
                            + "  2) 设置环境变量 CONTACT_KEY\n"
                            + "  3) 启动参数 -Dcontact.key=<Base64 密钥>\n"
                            + "生成一个 256 位密钥：openssl rand -base64 32");
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "contact.key 不是合法的 Base64 字符串，请用 `openssl rand -base64 32` 重新生成", e);
        }

        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("contact.key 解码后应为 16 / 24 / 32 字节"
                    + "（对应 AES-128 / 192 / 256），当前为 " + key.length + " 字节");
        }
        return new SecretKeySpec(key, "AES");
    }
}
