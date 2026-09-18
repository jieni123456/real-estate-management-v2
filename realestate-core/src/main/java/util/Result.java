package util;

/**
 * 操作结果：成功与否 + 面向用户的说明文字。
 *
 * <p>用于替代原先的裸 {@code boolean} 返回。原先 Controller 只能回答「成没成」，
 * 无法告诉界面「为什么没成」（必填未填？ID 已存在？面积不合法？），
 * 界面于是只能一律提示「添加失败」——用户看了也不知道该改哪里。
 *
 * <p>对应需求报告 G-003。
 */
public final class Result {

    private static final Result OK = new Result(true, "");

    private final boolean success;
    private final String message;

    private Result(boolean success, String message) {
        this.success = success;
        this.message = message == null ? "" : message;
    }

    /** 成功且无需额外说明 */
    public static Result ok() {
        return OK;
    }

    /** 成功，并附带一句说明（例如「房东已存在，沿用原信息」） */
    public static Result ok(String message) {
        return new Result(true, message);
    }

    /** 失败，message 为给用户看的原因 */
    public static Result fail(String message) {
        return new Result(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return (success ? "成功" : "失败") + (message.isEmpty() ? "" : "：" + message);
    }
}
