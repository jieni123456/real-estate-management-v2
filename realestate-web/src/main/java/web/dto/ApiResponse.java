package web.dto;

/**
 * 统一响应外壳：{@code {code, message, data}}。对应需求报告 R-004 / G-023。
 *
 * <p>为什么不让 core 的 {@code Result} 直接当返回体：{@code Result} 只回答
 * 「成功与否 + 一句给用户看的话」，是给 Swing 弹窗设计的；HTTP 还需要状态码、
 * 需要承载数据、需要区分「参数不合法」与「服务器出错」。
 *
 * <p>这里刻意只做「套一层」——<b>不去改 Result</b>，这样桌面端与网页端能共用同一份
 * 业务返回（需求报告 5.6 第 4 条）。
 *
 * <p>约定：{@code code} 成功时为 0，失败时与 HTTP 状态码一致（400 / 401 / 403 /
 * 409 / 500 / 503），前端只看这一个字段就能决定怎么提示。
 *
 * @param <T> 业务数据类型；无数据时用 {@code Void}
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
        // Jackson 反序列化需要
    }

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data);
    }

    /** 成功，并附带一句说明（例如「房屋添加成功（房东已存在，沿用其原有信息）」） */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
