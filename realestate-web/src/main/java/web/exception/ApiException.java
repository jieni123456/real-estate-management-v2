package web.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：把「要给调用方看的状态码与说明」随异常一起抛出，
 * 由 {@code GlobalExceptionHandler} 统一转成响应体。对应需求报告 G-023。
 *
 * <p>好处是 Service / Controller 里不必层层返回错误码——不满足条件就直接抛，
 * 正常路径的代码保持线性可读。
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final int code;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.code = status.value();
    }

    /** 参数不合法、缺少必填项 */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** 未登录，或 token 无效 / 已过期 */
    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    /** 已登录但权限不足 */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    /** 目标记录不存在 */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
