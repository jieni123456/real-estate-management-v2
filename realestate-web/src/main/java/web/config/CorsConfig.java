package web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域（CORS）配置。对应需求报告 R-004 阶段 2。
 *
 * <p><b>开发期其实用不到它。</b>前端经 Vite 代理访问后端（见 frontend/vite.config.js），
 * 从浏览器看是「同源」，压根不会触发跨域校验。本配置是给「前端与后端真的不同源」
 * 的场景准备的：把 dist 交给 nginx 托管、后端另占一个域名，或者前端跑
 * {@code npm run preview} 时端口与后端不同。
 *
 * <p>默认**关闭**：不配 {@code app.cors.allowed-origins} 就不会注册任何 CORS 规则。
 * 默认放开跨域是危险的——浏览器替用户挡住了别站的脚本读取本接口的响应，
 * 这个保护不该因为「图省事」被主动撤掉。需要时显式配置，例如：
 *
 * <pre>
 *   --app.cors.allowed-origins=http://localhost:5173,http://localhost:4173
 * </pre>
 *
 * <p><b>为什么用 Filter 而不是 WebMvcConfigurer.addCorsMappings：</b>
 * 后者的 CORS 处理发生在 DispatcherServlet 内部。当请求被拦截器 / 异常处理器
 * 直接写出 401、503 这类错误响应时，那些响应可能来不及带上 Access-Control-Allow-Origin
 * 头——浏览器的表现是「跨域被拦」，调用方连状态码都看不到，问题极难定位。
 * Filter 在 Servlet 链的更外层，它在委托之前就把响应头写好了，
 * 因此对成功与失败响应一视同仁。
 */
@Configuration
public class CorsConfig {

    /** 未配置时的占位值：空串表示不启用 CORS */
    private static final String DISABLED = "";

    @Bean
    public CorsFilter corsFilter(
            @Value("${app.cors.allowed-origins:" + DISABLED + "}") String allowedOrigins) {

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = split(allowedOrigins);

        if (origins.isEmpty()) {
            // 没有规则 = CorsFilter 只透传，不做任何 CORS 处理
            System.out.println("[CORS] 未配置 app.cors.allowed-origins，跨域未启用"
                    + "（开发期前端经 Vite 代理，无需此项）");
            return new CorsFilter(source);
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 用 Authorization 头传 token，不需要携带 Cookie；
        // 关掉它也就避开了「allowCredentials=true 时不能用 * 通配」那条限制
        config.setAllowCredentials(false);
        // 预检结果缓存 1 小时，减少 OPTIONS 往返
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", config);
        System.out.println("[CORS] 已启用，允许来源: " + origins);
        return new CorsFilter(source);
    }

    /** 逗号分隔转列表，顺带去掉空白项 —— 「配了但都是空格」应等同于没配 */
    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
