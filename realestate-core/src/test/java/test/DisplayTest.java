package test;

import util.Formats;
import util.SearchMatcher;

/**
 * 展示格式化与关键字匹配的测试。对应需求报告 G-004。
 *
 * <p>这两个类都是纯函数，也因此能在无界面、无数据库的环境下直接验证——
 * 当初把它们抽出来就是为了这个目的。
 */
public final class DisplayTest {

    private DisplayTest() {
    }

    public static void run(TestRunner t) {
        t.suite("Formats · 面积格式化");

        t.equals("整数去掉多余的 .0", "128", Formats.area(128.0));
        t.equals("一位小数保留", "89.5", Formats.area(89.5));
        t.equals("两位小数不做四舍五入（否则编辑保存会改库里的值）",
                "89.25", Formats.area(89.25));
        t.equals("NaN 显示占位符", "—", Formats.area(Double.NaN));
        t.equals("无穷大显示占位符", "—", Formats.area(Double.POSITIVE_INFINITY));

        t.suite("Formats · 平均值格式化");

        t.equals("平均值保留一位小数", "112.8", Formats.average(112.75));
        t.equals("平均值整数不带 .0", "120", Formats.average(120.0));
        t.equals("平均值为 0 时显示占位符", "—", Formats.average(0));
        t.equals("平均值为负时显示占位符", "—", Formats.average(-1));
        t.equals("平均值为 NaN 时显示占位符", "—", Formats.average(Double.NaN));

        t.suite("Formats · 日期时间格式化（G-008）");

        t.equals("标准格式 yyyy-MM-dd HH:mm",
                "2026-09-16 14:30",
                Formats.dateTime(java.time.LocalDateTime.of(2026, 9, 16, 14, 30)));
        t.equals("个位数月日与时刻补零",
                "2026-01-05 09:07",
                Formats.dateTime(java.time.LocalDateTime.of(2026, 1, 5, 9, 7)));
        t.equals("为 null 时返回空串（列表里不该出现 null 字样）",
                "", Formats.dateTime(null));
        t.equals("展示格式常量与用法一致", "yyyy-MM-dd HH:mm", Formats.DATE_TIME_PATTERN);

        t.suite("SearchMatcher · 关键字匹配（G-004）");

        t.check("null 关键字视为不过滤", SearchMatcher.matches(null, "任意内容"));
        t.check("空串视为不过滤", SearchMatcher.matches("", "abc"));
        t.check("纯空白视为不过滤", SearchMatcher.matches("   ", "abc"));
        t.check("命中子串", SearchMatcher.matches("abc", "xxabcxx"));
        t.check("大小写不敏感", SearchMatcher.matches("ABC", "xxabcxx"));
        t.check("不命中返回 false", !SearchMatcher.matches("abc", "xyz"));
        t.check("字段为 null 不崩溃", SearchMatcher.matches("张", null, "张三"));
        t.check("全部字段为 null 时不命中",
                !SearchMatcher.matches("zzz", (String) null));

        t.check("多关键字需全部命中（AND）",
                SearchMatcher.matches("1010 两室", "1010", "两室一厅", "某某路"));
        t.check("多关键字部分命中即算不匹配",
                !SearchMatcher.matches("1010 三室", "1010", "两室一厅"));
        t.check("多余空格不影响",
                SearchMatcher.matches("  1010    两室  ", "1010", "两室一厅"));
        t.check("关键字可跨字段命中（地址 + 房东姓名）",
                SearchMatcher.matches("阳光路 李", "1010", "两室一厅", "阳光路 8 号",
                        "L01", "李四", "138"));

        t.check("isBlank 对 null 为 true", SearchMatcher.isBlank(null));
        t.check("isBlank 对纯空白为 true", SearchMatcher.isBlank("  "));
        t.check("isBlank 对内容为 false", !SearchMatcher.isBlank("a"));
    }
}
