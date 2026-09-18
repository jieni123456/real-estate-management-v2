package test;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简测试框架。对应需求报告 G-016。
 *
 * <p>为什么不用 JUnit：本项目没有 Maven / Gradle，引入 JUnit 需要额外下载 jar、
 * 配置 classpath、区分测试源码根目录，成本明显高于收益。这里用「纯 Java +
 * javac/java 即可运行」的方式实现断言与汇总，零新增依赖，
 * 不需要 IDE 或构建工具就能跑。
 *
 * <p>运行方式见 test/README 或需求报告 G-016 条目。
 */
public final class TestRunner {

    private final List<String> failures = new ArrayList<>();
    private int passed;
    private int failed;
    private String suite = "";

    /** 开始一组测试，仅用于让输出分段清晰 */
    public void suite(String name) {
        this.suite = name;
        System.out.println();
        System.out.println("--- " + name + " ---");
    }

    /** 布尔断言 */
    public void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[通过] " + name);
        } else {
            failed++;
            failures.add(suite + " / " + name);
            System.out.println("[失败] " + name);
        }
    }

    /** 相等断言。把期望值与实际值一并打印出来，失败时不必再去复现 */
    public void equals(String name, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        check(name + "（期望 " + expected + "，实际 " + actual + "）", same);
    }

    /** 断言为 null */
    public void isNull(String name, Object value) {
        check(name + "（期望 null，实际 " + value + "）", value == null);
    }

    /** 断言非 null，并返回它以便继续断言 */
    public <T> T notNull(String name, T value) {
        check(name + "（不应为 null）", value != null);
        return value;
    }

    /**
     * 输出汇总。
     *
     * @return 失败项数，供 main 决定退出码（0 = 全部通过）
     */
    public int report() {
        System.out.println();
        System.out.println("========== 通过 " + passed + " 项，失败 " + failed + " 项 ==========");
        if (!failures.isEmpty()) {
            System.out.println("失败项：");
            for (String failure : failures) {
                System.out.println("  · " + failure);
            }
        }
        return failed;
    }
}
