package web.config;

import com.zaxxer.hikari.HikariDataSource;
import dao.DatabaseUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import util.Session;
import web.security.ThreadLocalUserHolder;

import javax.sql.DataSource;

/**
 * core 与 Spring 的接驳点。对应需求报告 R-004 / G-021 / G-022。
 *
 * <p>core 刻意不带框架注解——它还要被无框架的桌面端复用，所以「两边怎么接上」
 * 全部集中在本类。看这一个文件就知道 Web 端往 core 里塞了什么：
 *
 * <ol>
 *   <li>把 HikariCP 连接池交给 {@code DatabaseUtil}，DAO 从此走池取连接；</li>
 *   <li>把「当前用户存在哪」换成 {@link ThreadLocalUserHolder}，并发请求才不会互相顶掉身份。</li>
 * </ol>
 *
 * <p>桌面端这两件事都不做，于是保持 DriverManager 直连 + 静态字段的原样。
 */
@Configuration
public class CoreIntegrationConfig {

    private final ThreadLocalUserHolder userHolder;

    public CoreIntegrationConfig(ThreadLocalUserHolder userHolder) {
        this.userHolder = userHolder;
    }

    /** 启动时替换掉 Session 的默认实现（静态字段 → 每请求一份） */
    @PostConstruct
    void wireCurrentUser() {
        Session.setHolder(userHolder);
        System.out.println("[core 接驳] 当前用户来源：ThreadLocal（每请求一份）");
    }

    /**
     * 连接池。
     *
     * <p>连接参数直接复用 core 已解析好的值，不另立一套 {@code spring.datasource.*}——
     * 配置只有一个来源（db.properties / 环境变量），少一处能配错的地方。
     *
     * <p>{@code initializationFailTimeout(-1)} 表示启动时不试连：数据库暂时不可用
     * 不该让服务起不来。真正的失败会在第一次请求时以 503 加明确说明返回
     * （见 GlobalExceptionHandler 对 DataAccessException 的映射）。
     */
    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();

        ds.setJdbcUrl(DatabaseUtil.configuredUrl());
        ds.setUsername(DatabaseUtil.configuredUser());
        ds.setPassword(DatabaseUtil.configuredPassword());

        ds.setPoolName("realestate-pool");
        // 单门店内部系统的量级，10 条连接足够；调大只会占住数据库的连接数
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(10_000);
        ds.setInitializationFailTimeout(-1);

        // 交给 core：DAO 全都通过 DatabaseUtil.getConnection() 取连接，换掉来源即可
        DatabaseUtil.setDataSource(ds);

        System.out.println("[core 接驳] 连接池已就绪：" + DatabaseUtil.configuredUrl());
        return ds;
    }
}
