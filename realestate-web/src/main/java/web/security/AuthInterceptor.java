package web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import util.Session;
import web.exception.ApiException;

import java.util.List;
import java.util.Optional;

/**
 * 登录校验。对应需求报告 R-004 / G-022。
 *
 * <p>它做三件事：从 {@code Authorization: Bearer <token>} 取 token → 校验并解出用户
 * → 写入 {@link ThreadLocalUserHolder}，于是本次请求里所有
 * {@code Session.can(...)} 的调用（core 的 controller 里）都自动拿到正确的用户。
 * 请求结束时清空，避免复用的线程把上一个请求的用户留给下一个请求。
 *
 * <p><b>为什么放行路径写在拦截器里、而不是用 excludePathPatterns：</b>
 * 被 exclude 掉的路径不会执行 {@code afterCompletion}。而登录接口本身会调用
 * {@code AuthController.login}，那里会把用户写进当前线程——若不清理，Tomcat 复用
 * 这个线程处理下一个请求时，那个请求会「自带」上一个登录用户。所以登录接口也必须
 * 被拦截器覆盖，只是不放行校验而已。
 *
 * <p>拒绝时抛 {@link ApiException}，由 {@code GlobalExceptionHandler} 统一转成
 * {@code {code, message, data}}，响应格式与本模块其它接口保持一致。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 无需登录即可访问的路径。其余 /api/** 一律要求带 token */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/health");

    private final JwtUtil jwtUtil;
    private final ThreadLocalUserHolder userHolder;

    public AuthInterceptor(JwtUtil jwtUtil, ThreadLocalUserHolder userHolder) {
        this.jwtUtil = jwtUtil;
        this.userHolder = userHolder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // 跨域预检请求不带 Authorization 头，放行（CORS 配置在第 2 阶段接入）
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (PUBLIC_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw ApiException.unauthorized(
                    "未登录：请在请求头带上 Authorization: Bearer <token>");
        }

        Optional<User> user = jwtUtil.parse(header.substring(BEARER_PREFIX.length()).trim());
        if (user.isEmpty()) {
            throw ApiException.unauthorized("登录状态已失效（token 无效或已过期），请重新登录");
        }

        userHolder.set(user.get());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程会被 Tomcat 复用，必须清干净——否则下一个请求可能冒用上一个用户的身份
        Session.logout();
    }
}
