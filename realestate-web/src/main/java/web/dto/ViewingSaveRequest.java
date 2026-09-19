package web.dto;

/**
 * 新增 / 编辑带看记录的请求体。对应需求报告 R-004 阶段 4。
 *
 * <p>{@code viewedAt} 用 core 的 {@code Formats.DATE_TIME_SECONDS_PATTERN}
 * （{@code yyyy-MM-dd HH:mm:ss}）收发，前端 {@code el-date-picker} 的 value-format
 * 配成同一个模式，回填与提交因此是对称的。
 *
 * <p>解析与校验都不在这里做：
 * <ul>
 *   <li>格式不对 → 接口层直接回 400 并说明格式；</li>
 *   <li>「不能为空」「不能晚于今天」「结果取值是否合法」「客户/房屋是否存在」
 *       「房源是否已租出」→ 全在 core 的 {@code ViewingController} 里判定。</li>
 * </ul>
 *
 * @param customerId 客户ID，必须存在于 customers 表
 * @param houseId    房屋ID，必须存在且**处于「空置」状态**（G-020）
 * @param viewedAt   带看时间，{@code yyyy-MM-dd HH:mm:ss}
 * @param result     带看结果：意向中 / 已成交 / 无意向
 * @param note       备注，可空
 */
public record ViewingSaveRequest(String customerId,
                                 String houseId,
                                 String viewedAt,
                                 String result,
                                 String note) {
}
