package util;

/**
 * 操作结果：成功与否 + 面向用户的说明文字。
 *
 * <p>用于替代原先的裸 {@code boolean} 返回。原先 Controller 只能回答「成没成」，
 * 无法告诉界面「为什么没成」（必填未填？ID 已存在？面积不合法？），
 * 界面于是只能一律提示「添加失败」——用户看了也不知道该改哪里。
 *
 * <p>对应需求报告 G-003。
 *
 * <p>{@link Kind} 是 R-004 阶段 3 新增的：桌面端只用「成功与否 + 说明」，
 * 而 HTTP 还需要一个<u>状态码</u>——「ID 已存在」应是 409、「记录不存在」应是 404、
 * 「权限不足」应是 403，全都压成 400 会让调用方分不清该改什么。
 * 失败原因的分类沿用 {@link DataAccessException} 已经用过的写法：在源头标一次，
 * 由上层翻译，而不是让上层去猜文字内容。
 */
public final class Result {

    /** 失败原因的分类。桌面端不关心，Web 端据此决定 HTTP 状态码 */
    public enum Kind {
        /** 参数或业务校验不通过 */
        VALIDATION,
        /** 与已有数据冲突，例如 ID 已被占用 */
        CONFLICT,
        /** 目标记录不存在（或已被他人删除） */
        NOT_FOUND,
        /** 已登录但权限不足 */
        PERMISSION,
        /** 其它业务失败，上层按通用错误处理 */
        OTHER
    }

    private static final Result OK = new Result(true, "", Kind.OTHER);

    private final boolean success;
    private final String message;
    private final Kind kind;

    private Result(boolean success, String message, Kind kind) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.kind = kind == null ? Kind.OTHER : kind;
    }

    /** 成功且无需额外说明 */
    public static Result ok() {
        return OK;
    }

    /** 成功，并附带一句说明（例如「房东已存在，沿用原信息」） */
    public static Result ok(String message) {
        return new Result(true, message, Kind.OTHER);
    }

    /**
     * 失败，message 为给用户看的原因。
     *
     * <p>分类为 {@link Kind#OTHER}。需要精确表达失败类型时用
     * {@link #fail(Kind, String)} —— 例如「ID 已存在」应标为 {@link Kind#CONFLICT}。
     */
    public static Result fail(String message) {
        return new Result(false, message, Kind.OTHER);
    }

    /** 失败，并明确失败类型 */
    public static Result fail(Kind kind, String message) {
        return new Result(false, message, kind);
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

    /** 失败原因的分类；成功时无意义 */
    public Kind getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return (success ? "成功" : "失败") + (message.isEmpty() ? "" : "：" + message);
    }
}
