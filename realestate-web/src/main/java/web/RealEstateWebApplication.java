package web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端服务入口。对应需求报告 R-004。
 *
 * <p>基包为 {@code web}：{@code @SpringBootApplication} 会扫描本包及其子包，
 * 因此 {@code web.controller} 下的接口会被自动注册；而 core 里的
 * {@code model / dao / service / controller} 不在扫描范围内——它们刻意不带
 * Spring 注解，因为无框架的桌面端也要复用同一套代码，装配由配置类显式完成。
 *
 * <p>启动方式：
 *
 * <pre>
 *   ./mvnw -pl realestate-web spring-boot:run
 *   或
 *   java -jar realestate-web/target/realestate-web-1.0.0-SNAPSHOT.jar
 * </pre>
 *
 * <p>数据库与连接池（HikariCP）、声明式事务、JWT 鉴权等在第 1 阶段接入。
 */
@SpringBootApplication
public class RealEstateWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealEstateWebApplication.class, args);
    }
}
