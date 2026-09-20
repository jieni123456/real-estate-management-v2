package web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（OpenAPI / Swagger UI）。对应需求报告 R-004 阶段 5。
 *
 * <p>文档页面地址：
 * <ul>
 *   <li>Swagger UI：{@code http://localhost:8080/swagger-ui/index.html}</li>
 *   <li>OpenAPI JSON：{@code http://localhost:8080/v3/api-docs}</li>
 * </ul>
 *
 * <p>这里最关键的一件事是**声明 Bearer token 的鉴权方案**：本项目的接口除了登录与
 * 健康检查都要带 token，如果文档里不声明，使用者在 Swagger UI 上点「Try it out」
 * 只会拿到 401，却不知道差在哪。声明之后右上角会出现 Authorize 按钮，
 * 把 token 粘进去即可正常试调。
 *
 * <p>写法上刻意**只定义一个 {@code OpenAPI} Bean**，把 components 与 security
 * 都挂在这个对象上：springdoc 取的就是这一个 Bean，另起 {@code Components} /
 * {@code SecurityRequirement} 的独立 Bean 不会被合并进来，只是看着像生效了。
 *
 * <p>说明：这两个路径都不在 {@code /api/**} 下，因此不会被
 * {@link web.security.AuthInterceptor} 拦截，无需登录即可打开 —— 这是刻意的，
 * 文档本身不含数据。若将来要对外部署，应把接口文档关掉或加访问限制
 * （见 5.17 的「已知取舍」）。
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI realEstateOpenApi() {
        Components components = new Components().addSecuritySchemes(SCHEME_NAME,
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("登录接口返回的 token（粘贴时不需要写 Bearer 前缀）"));

        return new OpenAPI()
                .info(new Info()
                        .title("二手房中介管理系统 · 接口文档")
                        .version("1.0.0")
                        .description("""
                                同一套业务逻辑（realestate-core）支撑两个界面：Swing 桌面端与本服务。
                                本服务只提供 JSON，页面由 frontend/ 里的 Vue 3 应用负责。

                                使用步骤：
                                  1. 调 POST /api/auth/login 拿到 token
                                  2. 点右上角 Authorize，把 token 粘进去
                                  3. 其余接口即可正常试调
                                """))
                // 全局安全要求：默认每个接口都需要 token。
                // 放行的两个接口（健康检查、登录）在文档里仍会显示锁图标 ——
                // 全局要求无法逐接口取消，实际行为以代码里的放行名单为准。
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
