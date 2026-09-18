package model;

import util.SecurityUtil;

/**
 * 用户。
 *
 * <p>注意构造函数接收的是<b>明文口令</b>，内部立即算出加盐哈希——这是为了防止
 * 「不小心把明文存进对象」而做的设计。由此带来一个副作用：构造 User 隐含一次
 * 加密计算，所以从已认证信息还原用户时不要走这个构造函数，用
 * {@link #of(String, String)}。对应需求报告 G-022。
 */
public class User {

    private String username;
    private String encryptedPassword;
    private String role;

    public User(String username, String password, String role) {
        this.username = username;
        this.encryptedPassword = SecurityUtil.encryptPassword(password);
        this.role = role;
    }

    /** 内部用：只装身份，不涉及口令 */
    private User(String username, String role) {
        this.username = username;
        this.encryptedPassword = "";
        this.role = role;
    }

    /**
     * 由已知的用户名与角色还原用户对象，不涉及任何口令计算。
     *
     * <p>Web 端从 JWT 解出身份后用它构造「当前用户」——每个请求都要走一遍，
     * 若沿用带口令的构造函数，等于每次请求都白算一次哈希。
     */
    public static User of(String username, String role) {
        return new User(username, role);
    }

    // Getters and setters
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getRole() { return role; }
}
