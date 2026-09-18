package webtest;

import test.TestRunner;

/**
 * 后端模块的单元测试入口。对应需求报告 G-016。
 *
 * <p>由 realestate-web/pom.xml 的 exec 插件绑到 test 阶段执行，
 * 因此 {@code ./mvnw test} 会连同 core 的测试一起跑完。
 *
 * <p>它复用 core 的 test-jar 里的 {@link TestRunner}——断言框架只写一份。
 * 全部用例不依赖数据库与显示环境，可在无头机器上运行。
 */
public final class WebAllTests {

    private WebAllTests() {
    }

    public static void main(String[] args) {
        System.out.println("========== 二手房中介管理系统 · 后端模块单元测试 ==========");

        TestRunner runner = new TestRunner();

        JwtUtilTest.run(runner);

        int failed = runner.report();
        System.exit(failed == 0 ? 0 : 1);
    }
}
