package view;

import controller.AuthController;
import ui.Theme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 登录界面。
 *
 * <p>按需求报告 R-002 第 4 轮决定采用「左右分栏」：左侧品牌区承载系统名与功能文案，
 * 右侧为登录表单。窗口尺寸相应为 760 × 460。
 */
public class LoginView extends JPanel {

    /** 品牌区固定宽度，按 760 宽的 40% 计算 */
    private static final int BRAND_WIDTH = 304;
    /** 表单右栏内容宽度 */
    private static final int FORM_WIDTH = 320;

    private final AuthController authController;
    private final Runnable onLoginSuccess;

    private final JTextField userField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JButton loginButton = new JButton("登录");

    public LoginView(AuthController authController, Runnable onLoginSuccess) {
        this.authController = authController;
        this.onLoginSuccess = onLoginSuccess;

        setLayout(new BorderLayout());
        add(createBrandPanel(), BorderLayout.WEST);
        add(createFormPanel(), BorderLayout.CENTER);
    }

    // ------------------------------------------------------------ 左侧品牌区

    private JPanel createBrandPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.ACCENT);
        panel.setPreferredSize(new Dimension(BRAND_WIDTH, 0));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("<html>二手房<br>中介管理系统</html>");
        title.setFont(Theme.FONT_BRAND);
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel features = new JLabel("<html>房源管理<br>客户管理<br>一站完成</html>");
        features.setFont(Theme.FONT_CAPTION);
        features.setForeground(Theme.ACCENT_LIGHT);
        features.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(16));
        content.add(features);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 34, 0, 24);
        panel.add(content, gbc);
        return panel;
    }

    // ------------------------------------------------------------ 右侧表单

    private JPanel createFormPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.SURFACE);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(FORM_WIDTH, 262));

        JLabel welcome = new JLabel("欢迎回来");
        welcome.setFont(Theme.FONT_BODY);
        welcome.setForeground(Theme.TEXT_SECONDARY);
        welcome.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(welcome);
        form.add(Box.createVerticalStrut(18));
        form.add(createFieldLabel("用户名"));
        form.add(Box.createVerticalStrut(6));
        form.add(configureField(userField));
        form.add(Box.createVerticalStrut(14));
        form.add(createFieldLabel("密码"));
        form.add(Box.createVerticalStrut(6));
        form.add(configureField(passField));
        form.add(Box.createVerticalStrut(22));
        form.add(createLoginButton());

        outer.add(form);
        return outer;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_CAPTION);
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextField configureField(JTextField field) {
        field.setFont(Theme.FONT_BODY);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        field.setPreferredSize(new Dimension(FORM_WIDTH, 34));
        return field;
    }

    private JButton createLoginButton() {
        loginButton.setFont(Theme.FONT_BODY);
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        loginButton.setPreferredSize(new Dimension(FORM_WIDTH, 36));
        loginButton.setBackground(Theme.ACCENT);
        loginButton.setForeground(Theme.TEXT_ON_ACCENT);
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginButton.addActionListener(e -> doLogin());
        return loginButton;
    }

    private void doLogin() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            Toast.error(this, "请输入用户名和密码");
            return;
        }

        if (authController.login(username, password) != null) {
            onLoginSuccess.run();
        } else {
            Toast.error(this, "用户名或密码错误");
            passField.setText("");
            passField.requestFocusInWindow();
        }
    }

    /** 退出登录后清空表单，避免密码残留在输入框里 */
    public void reset() {
        userField.setText("");
        passField.setText("");
        userField.requestFocusInWindow();
    }

    /** 让回车键直接触发登录 */
    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.getRootPane(this).setDefaultButton(loginButton);
    }
}
