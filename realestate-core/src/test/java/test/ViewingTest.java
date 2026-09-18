package test;

import model.Viewing;

/**
 * Viewing 模型的测试。对应需求报告 G-008。
 *
 * <p>带看结果是这条记录最核心的字段（成交与否全靠它），而它来自下拉框——
 * 一旦有人往数据库里塞了别的值，统计就会静默失真。因此把「取值合法性」
 * 做成纯函数并锁上测试。
 */
public final class ViewingTest {

    private ViewingTest() {
    }

    public static void run(TestRunner t) {
        t.suite("Viewing · 带看结果取值（G-008）");

        t.check("意向中是合法值", Viewing.isValidResult(Viewing.RESULT_INTENT));
        t.check("已成交是合法值", Viewing.isValidResult(Viewing.RESULT_DEAL));
        t.check("无意向是合法值", Viewing.isValidResult(Viewing.RESULT_REJECT));
        t.check("null 不合法", !Viewing.isValidResult(null));
        t.check("空串不合法", !Viewing.isValidResult(""));
        t.check("大小写不同不合法", !Viewing.isValidResult("已成交 "));
        t.check("未知值不合法", !Viewing.isValidResult("随便写的"));
        t.equals("可选值共 3 个", 3, Viewing.RESULTS.length);

        // 三个常量必须都在可选值列表里，否则下拉框选不出某个状态
        for (String result : Viewing.RESULTS) {
            t.check("可选值列表包含「" + result + "」", Viewing.isValidResult(result));
        }

        t.suite("Viewing · 是否成交的判断");

        Viewing deal = new Viewing(1L, "C1", "张三", "101", "阳光路 8 号",
                java.time.LocalDateTime.of(2026, 9, 16, 14, 30), Viewing.RESULT_DEAL, "已签约");
        Viewing intent = new Viewing(2L, "C2", "李四", "102", "阳光路 9 号",
                java.time.LocalDateTime.of(2026, 9, 16, 15, 0), Viewing.RESULT_INTENT, "");

        t.check("已成交记录 isDeal 为 true", deal.isDeal());
        t.check("意向中记录 isDeal 为 false", !intent.isDeal());
        t.equals("新增占位 ID 为 0", 0L, Viewing.NEW_ID);
        t.equals("客户姓名可直接取用（来自 JOIN）", "张三", deal.getCustomerName());
        t.equals("房屋地址可直接取用（来自 JOIN）", "阳光路 9 号", intent.getHouseAddress());
    }
}
