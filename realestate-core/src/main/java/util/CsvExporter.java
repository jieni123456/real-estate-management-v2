package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV 导出。对应需求报告 G-014。
 *
 * <p>两个容易踩的点写在这里：
 * <ol>
 *   <li><b>转义</b>：字段里出现逗号、引号或换行时必须用双引号包起来，字段内的引号翻倍，
 *       否则一行数据会被 Excel 拆成好几列。</li>
 *   <li><b>UTF-8 BOM</b>：Excel 双击打开不带 BOM 的 UTF-8 文件会把中文显示成乱码。
 *       文件开头补一个 BOM 即解决——用户体验上的差别很大，成本却只有一行。</li>
 * </ol>
 *
 * <p>{@link #build(List)} 是不碰文件系统的纯函数，可以脱离界面单独验证。
 */
public final class CsvExporter {

    private static final String BOM = "\uFEFF";
    /** Excel 在 Windows 上对 CRLF 兼容最好 */
    private static final String LINE_SEPARATOR = "\r\n";

    private CsvExporter() {
    }

    /**
     * 把行数据拼成完整 CSV 文本（<b>含 BOM</b>）。
     *
     * <p>Web 端导出下载时用它：HTTP 响应没有「写文件」这一步，直接要最终文本。
     * 有了这个方法，BOM 这件事就只在<u>本类</u>里定义一次，
     * 不会出现「桌面端加了 BOM、网页端忘了加」导致 Excel 打开乱码的偏差。
     */
    public static String buildWithBom(List<String[]> rows) {
        return BOM + build(rows);
    }

    /** 把行数据拼成 CSV 文本（不含 BOM） */
    public static String build(List<String[]> rows) {
        StringBuilder builder = new StringBuilder();
        if (rows == null) {
            return builder.toString();
        }
        for (String[] row : rows) {
            builder.append(buildLine(row)).append(LINE_SEPARATOR);
        }
        return builder.toString();
    }

    public static String buildLine(String[] row) {
        StringBuilder builder = new StringBuilder();
        if (row == null) {
            return builder.toString();
        }
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(row[i]));
        }
        return builder.toString();
    }

    /** 按 CSV 规则转义单个字段 */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 写文件：UTF-8 编码 + BOM。
     *
     * @throws IOException 写失败（磁盘只读、路径不存在、文件被占用等）
     */
    public static void write(Path file, List<String[]> rows) throws IOException {
        Files.write(file, buildWithBom(rows).getBytes(StandardCharsets.UTF_8));
    }

    /** 默认文件名用的日期后缀，例如 20260916 */
    public static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
