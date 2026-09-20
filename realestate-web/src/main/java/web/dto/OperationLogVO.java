package web.dto;

/**
 * 一条操作日志（GET /api/logs）。
 *
 * <p>字段与 core 的 {@code model.OperationLog} 一一对应。日志里存的角色已经是
 * 中文显示名（core 的 {@code LogService} 在写入时就转换过），因此这里直接透出、
 * 不再做一次映射 —— 否则同一个字段会有两处转换逻辑。
 *
 * @param time     发生时间（数据库端生成的字符串）
 * @param operator 操作人用户名
 * @param role     角色显示名，未登录场景为空
 * @param action   动作，如「删除房屋」
 * @param target   操作对象，如房屋 ID
 * @param detail   补充说明，可为空
 */
public record OperationLogVO(
        String time,
        String operator,
        String role,
        String action,
        String target,
        String detail) {
}
