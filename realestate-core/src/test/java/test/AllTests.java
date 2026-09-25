package test;

/**
 * 单元测试入口。对应需求报告 G-016。
 *
 * <p>运行方式（已接入 Maven 的 test 阶段，由 exec 插件 fork 进程执行）：
 *
 * <pre>
 *   ./mvnw test                       跑 core 与 web 两个模块的全部单测
 *   ./mvnw -pl realestate-core test   只跑 core
 * </pre>
 *
 * <p>也可以脱离 Maven 直接跑：本框架零第三方依赖，把主源码与测试源码一起编译即可，
 * 编译输出目录换成自己的临时目录，主类就是本类。
 *
 * <p>注意加 {@code -Dstdout.encoding=UTF-8}：不加时 Java 在 Windows 控制台按 GBK 输出，
 * 中文断言名会显示成乱码。
 *
 * <p>全部用例都不依赖数据库与显示环境，可在无头机器上运行；
 * 需要连库校验的部分在需求报告里单独说明为「定向测试」，不属于单元测试。
 */
public final class AllTests {

    private AllTests() {
    }

    public static void main(String[] args) {
        System.out.println("========== 二手房中介管理系统 · 单元测试 ==========");

        TestRunner runner = new TestRunner();

        SecurityUtilTest.run(runner);
        PermissionsTest.run(runner);
        ValidatorsTest.run(runner);
        DisplayTest.run(runner);
        ExportTest.run(runner);
        HouseCsvTest.run(runner);
        ViewingTest.run(runner);
        HouseTest.run(runner);
        ViewingRulesTest.run(runner);
        HouseQueryTest.run(runner);
        QueryTest.run(runner);

        int failed = runner.report();
        System.exit(failed == 0 ? 0 : 1);
    }
}
