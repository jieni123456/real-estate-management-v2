package web.controller;

import controller.AuthController;
import model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import util.Permissions;
import web.dto.ApiResponse;
import web.dto.LoginRequest;
import web.dto.LoginResponse;
import web.dto.UserVO;
import web.exception.ApiException;
import web.security.JwtUtil;

import java.util.List;
import java.util.TreeSet;

/**
 * 登录相关接口。对应需求报告 R-004 / G-024。
 *
 * <p>本类只做「HTTP 语义」这一层——取参、判空、把 core 的结果翻译成响应体。
 * 真正的口令校验、日志留痕都在 core 的 {@link AuthController} 里，
 * 桌面端调的是同一个方法，两边不会出现两套登录规则。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    /** core 的业务入口。无框架注解，直接 new —— 装配见 CoreIntegrationConfig 的说明 */
    private final AuthController authController = new AuthController();

    private final JwtUtil jwtUtil;

    public AuthApiController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 登录。
     *
     * <p>失败与「字段没填」刻意返回不同的状态码与文案：前者是 401（凭据不对），
     * 后者是 400（请求本身不完整）。合成一个「登录失败」会让前端没法给出有用的提示。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        String username = trim(request.username());
        String password = request.password() == null ? "" : request.password();

        if (username.isEmpty() || password.isEmpty()) {
            throw ApiException.badRequest("请输入用户名和密码");
        }

        User user = authController.login(username, password);
        if (user == null) {
            throw ApiException.unauthorized("用户名或密码错误");
        }

        String token = jwtUtil.issue(user);
        return ApiResponse.ok("登录成功", new LoginResponse(
                token,
                "Bearer",
                jwtUtil.expireMinutes(),
                user.getUsername(),
                user.getRole(),
                Permissions.displayName(user.getRole())));
    }

    /**
     * 当前登录用户。前端刷新页面后用它恢复「我是谁、有哪些权限」。
     *
     * <p>身份不是从请求参数里读的，而是拦截器解析 token 后写进来的——
     * 调用方无法通过伪造参数冒充别人。
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        User current = authController.getCurrentUser();
        if (current == null) {
            throw ApiException.unauthorized("尚未登录");
        }
        return ApiResponse.ok(new UserVO(
                current.getUsername(),
                current.getRole(),
                Permissions.displayName(current.getRole()),
                // 排序后返回，输出稳定，便于前端与测试比对
                List.copyOf(new TreeSet<>(Permissions.of(current.getRole())))));
    }

    /**
     * 退出登录。
     *
     * <p><b>如实说明：</b>JWT 是无状态的，服务端没有保存会话，所以这个接口做不到
     * 「让 token 立刻失效」——它记一条操作日志并清掉本次请求的当前用户，真正的登出
     * 是客户端丢弃 token。等 token 自然过期即可；若确实需要服务端强制失效，
     * 得引入黑名单或改用有状态会话，那是另一个取舍。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authController.logout();
        return ApiResponse.ok("已退出登录", null);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
