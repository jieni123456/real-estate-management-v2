package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 展示用的格式化工具。
 *
 * <p>集中放置，避免同一套格式在多个界面里各写一遍（例如面积的「去掉多余 .0」
 * 原先只在 HouseView 内部实现，概览页出现后就需要用同一套规则）。
 */
public final class Formats {

    /** 日期时间的展示格式。带看记录的表格与对话框共用（G-008） */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    /**
     * 日期时间的传输格式（含秒）。对应需求报告 R-004 阶段 4。
     *
     * <p>接口与前端表单之间用它收发：前端 {@code el-date-picker} 的 value-format
     * 配成同一个模式，回填与提交就是对称的，不需要在前端再做一次格式转换。
     * <b>刻意不用 ISO 的 T 分隔写法</b>——秒为 0 时 ISO 会把秒省掉（{@code 10:18}），
     * 与带秒的写法混在一起，前端解析要写两套分支。
     */
    public static final String DATE_TIME_SECONDS_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private static final DateTimeFormatter DATE_TIME_SECONDS =
            DateTimeFormatter.ofPattern(DATE_TIME_SECONDS_PATTERN);

    private Formats() {
    }

    /**
     * 面积：128.0 显示为 128，89.5 保持 89.5。
     *
     * <p>刻意不做四舍五入——表格里显示的数值会被「编辑」对话框原样回填，
     * 若这里把 89.25 显示成 89.3，用户保存后就会把库里的值改成 89.3，
     * 属于静默的数据变更。
     */
    public static String area(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** 平均值一类的派生数值：保留一位小数，且不显示多余的 .0 */
    public static String average(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            return "—";
        }
        return area(Math.round(value * 10) / 10.0);
    }

    /** 日期时间：2026-09-16 14:30。为 null 时返回空串，避免列表里出现 null 字样 */
    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    /**
     * 日期时间（含秒）：2026-09-16 14:30:00。供接口把值交给表单回填。
     * 为 null 时返回空串。
     */
    public static String dateTimeWithSeconds(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_SECONDS);
    }

    /**
     * 解析 {@link #DATE_TIME_SECONDS_PATTERN} 格式的时间串。
     *
     * <p>刻意把「格式不对」与「时间不合理」分开：格式不对时返回 {@code null}，
     * 由调用方给出「时间格式不正确」这类明确提示；而返回的对象是否合理
     * （例如不能晚于今天）交给 {@code Validators} 判断。解析失败返回 null
     * 而不是抛异常，是为了让「用户填错」不表现为服务端 500。
     */
    public static LocalDateTime parseDateTimeWithSeconds(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_SECONDS);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
