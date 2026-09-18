package test;

/**
 * 单元测试入口。对应需求报告 G-016。
 *
 * <p>运行方式（项目没有构建工具，直接用 JDK 即可）：
 *
 * <pre>
 *   cd com.realestate
 *   javac -encoding UTF-8 -d C:/Temp/rg-build-check -cp "lib/flatlaf-3.7.2.jar;lib/mysql-connector-j-8.0.33.jar" $(find src test -name '*.java')
 *   java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "C:/Temp/rg-build-check;lib/flatlaf-3.7.2.jar;lib/mysql-connector-j-8.0.33.jar" test.AllTests
 * </pre>
 *
 * <p>不加编码参数时 Java 在 Windows 控制台按 GBK 输出，中文断言名会显示成乱码。
 *
 * <p>全部用例都不依赖数据库与显示环境，可在无头机器上运行：
 * 需要连库校验的部分已在需求报告中单独说明为「定向测试」，不属于单元测试。
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
        ViewingTest.run(runner);
        HouseTest.run(runner);
        ViewingRulesTest.run(runner);

        int failed = runner.report();
        System.exit(failed == 0 ? 0 : 1);
    }
}
