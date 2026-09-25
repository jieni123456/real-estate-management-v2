package util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 房屋 CSV 的表头定义与解析。对应需求报告 R-006（批量导入）。
 *
 * <p><b>为什么表头要收到这里来：</b>原先它有两份 —— 网页端的
 * {@code HouseApiController.CSV_HEADER} 与桌面端的 {@code HouseView.COLUMNS}，
 * 逐字相同但各写各的。只做导出时这只是冗余；一旦支持导入，导出的文件要能被
 * 原样导回来，表头就变成「两侧必须逐字一致」的契约 —— 再多一份定义迟早会漂移，
 * 而它漂移的表现是「自己导出的文件自己导不回来」，报错信息很难指向真正的原因。
 *
 * <p><b>为什么自己写解析而不用现成的库：</b>{@code realestate-core} 是零依赖模块，
 * 它同时被 Swing 端与 Spring 端依赖，多引一个库就是两份要带着走的负担。
 * 而 CSV 的规则本身很小，下面这份实现覆盖 BOM、CRLF/LF、引号转义三种情况。
 *
 * <p><b>与 {@link CsvExporter} 的对称关系：</b>{@code CsvExporter.escape} 写出去的
 * 引号规则，这里的 {@link #parse} 必须能原样读回来，否则「导出再导入」会丢字符。
 * 两边的规则由 {@code CsvRoundTripTest} 钉住。
 */
public final class HouseCsv {

    /**
     * 列顺序。导出与导入共用这一份，界面的表格列也用它。
     *
     * <p>顺序不是随意定的：「状态」跟在「地址」之后，既贴近「这是哪套房」的阅读顺序，
     * 也不至于被「房东电话」挤到看不见（R-003）。改动这里会同时改变界面列序、
     * 导出文件的列序，以及导入时对表头的校验结果 —— 三处一起变，这正是共用一份的目的。
     */
    public static final String[] HEADER =
            {"ID", "户型", "面积(m²)", "地址", "状态", "房东ID", "房东姓名", "房东电话"};

    private HouseCsv() {
    }

    /**
     * 把文件字节解成文本。
     *
     * <p><b>为什么不能直接按 UTF-8 解：</b>本系统导出的是 UTF-8 + BOM，原样导回来
     * 没问题；但用户很可能用 Excel 打开改几行再另存 —— 中文 Windows 上
     * 「CSV UTF-8」并不在「另存为」的默认位置，随手存出来的是 <b>GBK</b>。
     * 那时若按 UTF-8 硬解，中文表头会变成一串替换字符，接着表头校验失败，
     * 用户看到的是「表头不一致」—— 而他明明一个字都没改过。这类提示比错误本身
     * 更难排查，所以编码这一步必须在源头判对。
     *
     * <p>判断顺序：带 BOM 的一定是 UTF-8（本系统导出的就是）；没有 BOM 的先按
     * <b>严格</b> UTF-8 试解，解不通再当 GBK。用严格模式而不是替换模式是关键 ——
     * 前者遇到非法字节会抛异常，这个异常正是「它不是 UTF-8」的判据；后者会把
     * 非法字节悄悄换成替换字符，什么也判断不出来。
     */
    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            // GBK 是 Java 标准字符集，任何实现都支持，不会走到「不存在」这一步
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    /**
     * 解析 CSV 文本为逐行的字段数组。
     *
     * <p>能处理的实际情形：
     * <ul>
     *   <li>Excel 另存为 CSV 时写在文件开头的 <b>BOM</b>。不去掉的话第一个表头单元格
     *       会变成 {@code "\uFEFFID"}，表头校验必然失败，而用户在自己的编辑器里
     *       看到的文件并没有这个字符 —— 属于「怎么查都查不出来」的一类问题。</li>
     *   <li><b>CRLF</b>（Windows / Excel）与 <b>LF</b>（编辑器另存）两种行尾，
     *       以及孤立的 CR。混用同一文件也能正确切行。</li>
     *   <li><b>引号转义</b>：字段含逗号、引号或换行时由双引号包裹，字段内的引号
     *       写成两个。地址里出现逗号（「XX路1号,2单元」）是很常见的，不处理会把
     *       一行拆成两列。</li>
     *   <li>文件末尾的换行不会多产生一个空行；中间真正的空行被跳过。</li>
     * </ul>
     *
     * <p>解析<b>不做任何业务校验</b>：字段该不该空、状态合不合法、面积能不能转成数字，
     * 全交给 {@code HouseController.addHouse} 那条既有路径判断。这里刻意不抛异常，
     * 是因为「第 37 行有问题」应当是导入报告里的一条记录，而不是整批失败的理由。
     *
     * @return 每行一个 {@code String[]}，长度等于该行实际读到的列数（不补齐）。
     *         文件为空或全为空白时返回空列表。
     */
    public static List<String[]> parse(String text) {
        List<String[]> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }

        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int index = 0;
        int length = text.length();

        while (index < length) {
            char ch = text.charAt(index);

            if (inQuotes) {
                if (ch == '"') {
                    // 连续两个引号 = 字段内一个真正的引号；单个引号 = 引号段结束
                    if (index + 1 < length && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 2;
                        continue;
                    }
                    inQuotes = false;
                    index++;
                    continue;
                }
                // 引号段里的逗号与换行都是普通字符
                field.append(ch);
                index++;
                continue;
            }

            switch (ch) {
                case '"' -> {
                    inQuotes = true;
                    index++;
                }
                case ',' -> {
                    cells.add(field.toString());
                    field.setLength(0);
                    index++;
                }
                case '\n' -> {
                    collect(rows, cells, field);
                    index++;
                }
                case '\r' -> {
                    collect(rows, cells, field);
                    index++;
                    // CRLF：把紧跟的 \n 一并吃掉，否则会凭空多出一个空行
                    if (index < length && text.charAt(index) == '\n') {
                        index++;
                    }
                }
                default -> {
                    field.append(ch);
                    index++;
                }
            }
        }

        // 末行不一定有换行符收尾
        if (field.length() > 0 || !cells.isEmpty()) {
            collect(rows, cells, field);
        }
        return rows;
    }

    /** 收下当前这一行。整行都是空白的（文件末尾换行产生的）直接丢弃 */
    private static void collect(List<String[]> rows, List<String> cells, StringBuilder field) {
        cells.add(field.toString());
        field.setLength(0);
        boolean blank = cells.size() == 1 && cells.get(0).trim().isEmpty();
        if (!blank) {
            rows.add(cells.toArray(new String[0]));
        }
        cells.clear();
    }

    /**
     * 这批数据的第一行是不是本系统导出的表头。
     *
     * <p>拿别的表（客户、带看）或者随手整理的文件来导入时，应当<b>整批拒绝</b>并说明
     * 原因，而不是逐行报 100 条「字段不合法」—— 后者会让人以为是数据本身有问题。
     * 列名两端的空白做容错：在 Excel 里改过表头的人容易留下空格，
     * 而这并不影响「他就是想导这个文件」这个判断。
     */
    public static boolean isExpectedHeader(List<String[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        String[] cells = rows.get(0);
        if (cells == null || cells.length != HEADER.length) {
            return false;
        }
        for (int i = 0; i < HEADER.length; i++) {
            String actual = cells[i] == null ? "" : cells[i].trim();
            if (!HEADER[i].equals(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 表头不匹配时给用户看的说明：把期望的列名和实际读到的列名都摆出来。
     *
     * <p>只说「表头不正确」，用户多半会去逐字比对整份文件；把两串列名并排给出，
     * 一眼就能看出是导错了表，还是表头被 Excel 改过。
     */
    public static String describeHeaderMismatch(List<String[]> rows) {
        String actual = "（文件为空）";
        if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
            actual = String.join(", ", rows.get(0));
        }
        return "文件表头与房屋导出格式不一致。\n"
                + "  期望：" + String.join(", ", HEADER) + "\n"
                + "  实际：" + actual + "\n"
                + "请使用「导出 CSV」得到的文件，或确保表头与上表逐字一致。";
    }
}
