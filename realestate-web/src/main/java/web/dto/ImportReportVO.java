package web.dto;

import model.HouseImportReport;

import java.util.List;

/**
 * 批量导入的结果返回体。对应需求报告 R-006。
 *
 * <p>与 {@code Result} 那套「成功与否 + 一句话」不同，导入必须把<u>逐条</u>的失败
 * 明细交出去：用户手上是一份 100 行的文件，只告诉他「失败了 4 条」等于让他
 * 自己从头找一遍。行号按 CSV 文件里的行数（含表头，从 1 数起），
 * 这样他回到 Excel 或编辑器里能直接跳到那一行。
 *
 * @param total        参与导入的数据行数（不含表头）
 * @param success      成功写入的条数
 * @param failureCount 失败条数。与 {@code failures} 的长度相同，冗余给出是为了
 *                     前端不必自己算 —— 汇总数字与明细列表对不上是这类界面
 *                     最容易被用户发现的一类错误
 * @param failures     逐条失败明细，顺序与文件中的行序一致
 */
public record ImportReportVO(int total,
                             int success,
                             int failureCount,
                             List<FailureVO> failures) {

    /**
     * 一条未能导入的记录。
     *
     * @param line    CSV 文件中的行号（第 1 行是表头）
     * @param houseId 该行的房屋ID；连 ID 都没填时为空串
     * @param reason  用户能看懂的原因，原样取自 core 的校验 / 冲突提示
     */
    public record FailureVO(int line, String houseId, String reason) {
    }

    /** 由 core 的报告转换而来。逐条失败原因与行号在这里原样透传，不做二次加工 */
    public static ImportReportVO from(HouseImportReport report) {
        return new ImportReportVO(
                report.getTotal(),
                report.getSuccess(),
                report.getFailureCount(),
                report.getFailures().stream()
                        .map(item -> new FailureVO(
                                item.getLine(), item.getHouseId(), item.getReason()))
                        .toList());
    }
}
