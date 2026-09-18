package web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import web.security.AuthInterceptor;

/**
 * MVC 配置。对应需求报告 R-004 / G-022。
 *
 * <p>只做一件事：把登录拦截器挂到 {@code /api/**} 上。
 *
 * <p>这里用 addPathPatterns 而不是给登录/探活配 excludePathPatterns——放行名单写在
 * {@link AuthInterceptor} 内部，因为被 exclude 的路径不会执行 afterCompletion，
 * 而登录接口恰恰需要在请求结束时清理当前线程的用户（详见该类的说明）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
