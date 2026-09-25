package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量导入房屋的结果报告。对应需求报告 R-006。
 *
 * <p><b>为什么不是一个「成功 / 失败」的布尔：</b>导入 100 条时，用户需要的不是
 * 「失败了」这个结论，而是「哪几行、分别因为什么」。100 条里坏 4 条，剩下 96 条
 * 是好的 —— 全部回滚会让用户连那 96 条也拿不到，而且他手上除了「导入失败」
 * 之外没有任何线索去找问题所在。
 *
 * <p>逐行写入、跳过坏行的代价是「库里进了 96 条」这个事实必须讲清楚，
 * 不能让人以为要么全进要么全不进。{@link #summary()} 就是给这个场景用的。
 */
public class HouseImportReport {

    /**
     * 一条未能导入的记录。
     *
     * <p>记的是 <b>CSV 文件里的行号</b>（从 1 数起，第 1 行是表头）—— 用户拿着
     * 报告回到 Excel 或文本编辑器里能直接跳到那一行。若改写成「第 n 条数据」，
     * 用户还得自己换算一次，而这一步几乎一定会算错。
     */
    public static class Failure {

        private final int line;
        private final String houseId;
        private final String reason;

        public Failure(int line, String houseId, String reason) {
            this.line = line;
            this.houseId = houseId == null ? "" : houseId;
            this.reason = reason == null ? "" : reason;
        }

        public int getLine() {
            return line;
        }

        /** 该行的房屋ID。可能为空 —— 连 ID 都没填的行也要能报出来 */
        public String getHouseId() {
            return houseId;
        }

        /** 用户能看懂的原因，取自 core 既有的校验与冲突提示 */
        public String getReason() {
            return reason;
        }
    }

    private final boolean permitted;
    private final String denyReason;
    private final int total;
    private final int success;
    private final List<Failure> failures;

    private HouseImportReport(boolean permitted, String denyReason,
                              int total, int success, List<Failure> failures) {
        this.permitted = permitted;
        this.denyReason = denyReason == null ? "" : denyReason;
        this.total = total;
        this.success = success;
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
    }

    /** 权限不足，一行都没处理。此时 total / success 均为 0，不构成「导入结果」 */
    public static HouseImportReport denied(String reason) {
        return new HouseImportReport(false, reason, 0, 0, List.of());
    }

    public static HouseImportReport of(int total, int success, List<Failure> failures) {
        return new HouseImportReport(true, "", total, success,
                failures == null ? List.of() : failures);
    }

    /** 权限校验是否通过。为 false 时不要读 total / success，它们没有意义 */
    public boolean isPermitted() {
        return permitted;
    }

    public String getDenyReason() {
        return denyReason;
    }

    /** 参与导入的数据行数（不含表头） */
    public int getTotal() {
        return total;
    }

    public int getSuccess() {
        return success;
    }

    public int getFailureCount() {
        return failures.size();
    }

    public List<Failure> getFailures() {
        return failures;
    }

    /** 是否一条都没写进去（全部失败，或者文件里本来就没有数据行） */
    public boolean isNothingImported() {
        return success == 0;
    }

    /** 一句话结论，用于日志与界面提示：共 100 条，成功 96 条，失败 4 条 */
    public String summary() {
        if (!permitted) {
            return denyReason;
        }
        if (total == 0) {
            return "文件里没有可导入的数据行";
        }
        if (failures.isEmpty()) {
            return "共 " + total + " 条，全部导入成功";
        }
        return "共 " + total + " 条，成功 " + success + " 条，失败 " + failures.size() + " 条";
    }
}
