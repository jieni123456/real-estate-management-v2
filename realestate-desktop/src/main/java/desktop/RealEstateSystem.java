package desktop;

import controller.AuthController;
import controller.CustomerController;
import controller.HouseController;
import controller.LogController;
import controller.StatsController;
import controller.ViewingController;
import dao.DatabaseUtil;
import ui.Theme;
import view.LoginView;
import view.MainView;

import javax.swing.*;

public class RealEstateSystem {

    public static void main(String[] args) {
        // 必须在创建任何 Swing 组件之前完成外观与主题初始化
        Theme.init();

        // 初始化数据库
        DatabaseUtil.initializeDatabase();

        // 创建控制器
        AuthController authController = new AuthController();
        HouseController houseController = new HouseController();
        CustomerController customerController = new CustomerController();
        StatsController statsController = new StatsController();
        LogController logController = new LogController();
        ViewingController viewingController = new ViewingController();

        // 创建登录窗口
        JFrame loginFrame = new JFrame("登录");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(Theme.LOGIN_WIDTH, Theme.LOGIN_HEIGHT);
        loginFrame.setLocationRelativeTo(null);

        // 创建主窗口
        JFrame mainFrame = new JFrame("二手房中介管理系统");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(Theme.MAIN_WIDTH, Theme.MAIN_HEIGHT);
        mainFrame.setLocationRelativeTo(null);

        // 创建主视图面板（不是 JFrame）
        MainView mainView = new MainView(authController, houseController,
                customerController, viewingController, statsController, logController);
        mainFrame.add(mainView);
        mainFrame.setVisible(false); // 初始不显示

        // 创建登录视图
        LoginView loginView = new LoginView(authController, () -> {
            // 登录成功后隐藏登录窗口，显示主窗口
            loginFrame.setVisible(false);
            mainFrame.setVisible(true);
            mainView.showMainView(); // 启用主视图的功能，并按当前用户角色应用权限
        });

        // 退出登录：隐藏主窗口、清空登录表单、重新显示登录窗口。
        // 原先的实现只禁用了主窗口的控件却从未重新显示登录窗口，
        // 导致用户退出后卡在灰掉的主界面无法再登录（见需求报告 BUG-001）。
        mainView.setOnLogout(() -> {
            mainFrame.setVisible(false);
            loginView.reset();
            loginFrame.setVisible(true);
        });

        loginFrame.add(loginView);
        loginFrame.setVisible(true);
    }
}
