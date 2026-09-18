package dao;

import util.AppConfig;
import util.SecurityUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库连接工具。
 *
 * <p>连接参数的读取交给 {@link AppConfig}，优先级（从高到低）：
 * 系统属性 {@code db.url} / {@code db.user} / {@code db.password} →
 * 环境变量 {@code DB_URL} / {@code DB_USER} / {@code DB_PASSWORD} →
 * {@code db.properties} → 内置默认值。
 *
 * <p>{@code db.properties} 已被 .gitignore 排除，不会进版本库；模板见
 * {@code db.properties.example}。
 *
 * <p><b>本类不依赖任何界面库</b>（R-004）：它属于 core 层，桌面端与网页端共用。
 * 原先连库失败会弹 {@code JOptionPane}，改为只写 stderr——后端没有图形环境，
 * 弹窗会让服务启动即失败。
 */
public class DatabaseUtil {

    /**
     * 内置默认连接串。仅在既没有系统属性、也没有环境变量、
     * 也找不到 db.properties 时使用。
     *
     * <p>末尾的 {@code allowPublicKeyRetrieval=true} 是必需的（BUG-005）：
     * MySQL 8 的账号默认用 caching_sha2_password 插件，在未加密连接
     * （{@code useSSL=false}）下需要向服务端索取 RSA 公钥，默认被禁止，
     * 会报「Public Key Retrieval is not allowed」。该问题只在服务端认证缓存
     * 为空时出现（即每次重启 MySQL 后的第一次连接）。
     */
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/real_estate_db"
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "root";

    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        DB_URL = AppConfig.get("db.url", "DB_URL", "db.url", DEFAULT_URL);
        DB_USER = AppConfig.get("db.user", "DB_USER", "db.user", DEFAULT_USER);
        DB_PASSWORD = AppConfig.get("db.password", "DB_PASSWORD", "db.password", "");

        try {
            // 显式加载驱动类
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL JDBC 驱动加载成功!");
        } catch (ClassNotFoundException e) {
            System.err.println("找不到 MySQL JDBC 驱动!");
            e.printStackTrace();
            reportError("找不到 MySQL JDBC 驱动: " + e.getMessage()
                    + "\n请确认 mysql 驱动已在构建配置里声明（R-004 起依赖由 Maven 管理）。");
            throw new RuntimeException("找不到 MySQL JDBC 驱动", e);
        }

        if (DB_PASSWORD.isEmpty()) {
            System.out.println("[提示] 当前未配置数据库密码，请任选一种方式：");
            System.out.println("       1) 设置环境变量 DB_PASSWORD");
            System.out.println("       2) 复制 db.properties.example 为 db.properties 并填写密码");
        }
    }

    /**
     * 由外层注入的连接池。为 null 时回退到 DriverManager 直连。
     *
     * <p>core 只认 {@link javax.sql.DataSource}——那是 JDK 自带的接口，所以 Web 端
     * 可以把 HikariCP 塞进来，而 core 不必依赖任何连接池或框架；桌面端不注入，
     * 行为与改造前完全一致。对应需求报告 R-004 / G-021。
     */
    private static volatile DataSource dataSource;

    /**
     * 注入连接池。由 Web 端在启动时调用一次。
     *
     * <p>DAO 全部通过 {@link #getConnection()} 取连接，因此只要换掉这里的来源，
     * 上层一行都不用改——这也是当初把取连接的动作集中在本类的原因。
     */
    public static void setDataSource(DataSource ds) {
        dataSource = ds;
        System.out.println(ds == null
                ? "连接来源：DriverManager 直连（未注入连接池）"
                : "连接来源：已注入连接池 " + ds.getClass().getSimpleName());
    }

    /**
     * 已解析的连接串（已按 系统属性 → 环境变量 → db.properties → 内置默认值 取好）。
     *
     * <p>供 Web 端构造连接池时复用，避免它自己再读一遍配置、或把默认连接串抄第二份。
     */
    public static String configuredUrl() {
        return DB_URL;
    }

    /** 已解析的数据库账号（已按 系统属性 → 环境变量 → db.properties → 默认值 取好）。
     *  供 Web 端构造连接池时复用，避免它自己再读一遍配置。 */
    public static String configuredUser() {
        return DB_USER;
    }

    /** 已解析的数据库口令。仅供构造连接池使用：不打印、不写日志 */
    public static String configuredPassword() {
        return DB_PASSWORD;
    }

    public static Connection getConnection() throws SQLException {
        DataSource pooled = dataSource;
        if (pooled != null) {
            // 走连接池时不再逐次打印——池的意义就是复用连接，逐次打印只会淹没日志
            return pooled.getConnection();
        }

        System.out.println("连接数据库: " + DB_URL + " (用户: " + DB_USER + ")");
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            // 刻意不在这里弹模态框：连接失败的原因会被 DAO 归类为 DataAccessException，
            // 再由界面层转成用户能看懂的一句话（见需求报告 G-012）。
            // 顺带也避免了无头环境下弹框把线程卡住。
            System.err.println("数据库连接失败: " + e.getMessage());
            throw e;
        }
    }

    public static void initializeDatabase() {
        System.out.println("开始初始化数据库...");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建数据库（如果不存在）
            stmt.execute("CREATE DATABASE IF NOT EXISTS real_estate_db");
            stmt.execute("USE real_estate_db");

            // 创建用户表
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "username VARCHAR(50) PRIMARY KEY, " +
                    "encrypted_password VARCHAR(100) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL)");

            // 创建房东表
            stmt.execute("CREATE TABLE IF NOT EXISTS landlords (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "encrypted_contact TEXT NOT NULL)");

            // 创建房屋表。
            // status 为房屋状态（R-003）：空置 / 已租出。默认空置，
            // 这样既有的插入语句不写它也不会出错。
            stmt.execute("CREATE TABLE IF NOT EXISTS houses (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "type VARCHAR(50) NOT NULL, " +
                    "area DOUBLE NOT NULL, " +
                    "address VARCHAR(255) NOT NULL, " +
                    "landlord_id VARCHAR(50) NOT NULL, " +
                    "status VARCHAR(10) NOT NULL DEFAULT '空置', " +
                    "FOREIGN KEY (landlord_id) REFERENCES landlords(id))");

            // 创建客户表
            stmt.execute("CREATE TABLE IF NOT EXISTS customers (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "phone VARCHAR(20) NOT NULL, " +
                    "requirements TEXT)");

            // 创建操作日志表（G-017）。
            // created_at 用数据库端默认值，取服务器时间，比客户端时间更可信。
            stmt.execute("CREATE TABLE IF NOT EXISTS operation_logs (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "operator VARCHAR(50) NOT NULL DEFAULT '', " +
                    "role VARCHAR(20) NOT NULL DEFAULT '', " +
                    "action VARCHAR(30) NOT NULL DEFAULT '', " +
                    "target VARCHAR(100) NOT NULL DEFAULT '', " +
                    "detail VARCHAR(255) NOT NULL DEFAULT '', " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "INDEX idx_operation_logs_created_at (created_at))");

            // 创建带看记录表（G-008）。这是「客户 → 带看 → 成交」这条业务链的载体，
            // 也是 customers 与 houses 两张表之间唯一的关联。
            //
            // 外键刻意用 ON DELETE CASCADE：房屋或客户被删除后，对应的带看记录已无意义。
            // 但级联删除不能是隐形的——界面在删除确认框里会明确提示「将同时删除 N 条
            // 带看记录」，让用户知道自己在删什么。
            stmt.execute("CREATE TABLE IF NOT EXISTS viewings (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "customer_id VARCHAR(50) NOT NULL, " +
                    "house_id VARCHAR(50) NOT NULL, " +
                    "viewed_at DATETIME NOT NULL, " +
                    "result VARCHAR(20) NOT NULL DEFAULT '意向中', " +
                    "note VARCHAR(255) NOT NULL DEFAULT '', " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (house_id) REFERENCES houses(id) ON DELETE CASCADE, " +
                    "INDEX idx_viewings_viewed_at (viewed_at))");

            // R-003：为改造前建的库补上 houses.status 列。
            // 新建的库在上面的建表语句里已经带了该列，因此这里的判断只对旧库生效。
            // 刻意不用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS——那是 MariaDB 的扩展，
            // MySQL 不支持；改为先查 information_schema，重复执行也安全。
            if (!hasColumn(conn, "houses", "status")) {
                stmt.execute("ALTER TABLE houses ADD COLUMN status VARCHAR(10) "
                        + "NOT NULL DEFAULT '空置'");
                System.out.println("已为 houses 表补充 status 列（R-003）");
            }

            // 添加默认用户
            String adminPass = SecurityUtil.encryptPassword("admin123");
            String agentPass = SecurityUtil.encryptPassword("agent456");

            // 调试输出只打印密文，不打印口令本身
            System.out.println("初始管理员口令密文: " + adminPass);
            System.out.println("初始经纪人口令密文: " + agentPass);

            stmt.executeUpdate("INSERT IGNORE INTO users VALUES " +
                    "('admin', '" + adminPass + "', 'ADMIN'), " +
                    "('agent', '" + agentPass + "', 'AGENT')");

            System.out.println("数据库初始化成功!");

        } catch (SQLException e) {
            System.err.println("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
            reportError("数据库初始化失败: " + e.getMessage());
        }
    }

    /** 某张表是否已有某列。用于给旧库做可重复执行的结构补充（R-003） */
    private static boolean hasColumn(Connection conn, String table, String column)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 报告错误。
     *
     * <p>刻意只写 stderr、不弹窗：本类属于 core 层，而 core 不依赖 Swing。
     * 网页端是后端服务，没有图形环境，弹窗会导致启动即失败。
     * 提示如何呈现交由各界面层自行决定。
     */
    private static void reportError(String message) {
        System.err.println(message);
    }
}
