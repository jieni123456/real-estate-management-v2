
package model;

import util.SecurityUtil;

public class User {
    private String username;
    private String encryptedPassword;
    private String role;

    public User(String username, String password, String role) {
        this.username = username;
        this.encryptedPassword = SecurityUtil.encryptPassword(password);
        this.role = role;
    }

    // Getters and setters
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getRole() { return role; }
}