package web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 探活接口。这是阶段 0 的占位实现，用途只有一个：
 * 证明「Maven 构建 → Spring Boot 启动 → HTTP 响应」这条链路是通的。
 *
 * <p>它不涉及任何业务，真正的接口（登录、房屋列表等）在第 1 阶段加入，
 * 届时本类可以保留，也可以移到专门的运维接口里。
 *
 * <p>验证方式：
 *
 * <pre>
 *   curl http://localhost:8080/api/health
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", "二手房中介管理系统");
        body.put("stage", "R-004 阶段 0：骨架已就绪");
        body.put("time", LocalDateTime.now().toString());
        return body;
    }
}
