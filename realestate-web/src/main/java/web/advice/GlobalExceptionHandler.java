package web.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import util.DataAccessException;
import web.dto.ApiResponse;
import web.exception.ApiException;

/**
 * 全局异常处理。对应需求报告 R-004 / G-023。
 *
 * <p>解决的问题：不加这一层时，任何未捕获的异常都会变成 Spring 默认的错误页
 * （一段 HTML 加一串堆栈），前端拿到既解析不了、又把内部细节暴露了出去。
 *
 * <p>这里把三类异常分别归口：
 *
 * <ul>
 *   <li>{@link ApiException} —— 我们自己抛的业务异常（未登录、参数不合法……），
 *       按它自带的状态码原样返回，不打日志（它们是预期内的）。</li>
 *   <li>{@link DataAccessException} —— core 的 DAO 归类好的数据访问异常，
 *       按 {@code Kind} 映射状态码，文案用 {@code userMessage()}：
 *       给用户看的话与桌面端完全一致，驱动原始报错只进日志。</li>
 *   <li>其余异常 —— 兜底 500，日志留全堆栈，响应里不透露任何内部信息。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e) {
        HttpStatus status = switch (e.getKind()) {
            // 数据库连不上属于「服务暂时不可用」，不是调用方的错，故用 503 而非 500
            case CONNECTION -> HttpStatus.SERVICE_UNAVAILABLE;
            // 主键冲突、外键约束都是「与现有数据冲突」
            case DUPLICATE_KEY, CONSTRAINT -> HttpStatus.CONFLICT;
            case UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.error("数据访问失败 [{}] {}", e.getKind(), e.getMessage(), e);
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(status.value(), e.userMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(HttpStatus.BAD_REQUEST.value(),
                        "请求体不是合法的 JSON，或字段类型不匹配"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("未预期的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "服务器内部错误，请稍后重试"));
    }
}
