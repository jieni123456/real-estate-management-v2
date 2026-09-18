package test;

import model.House;
import model.Landlord;

/**
 * House 的测试。对应需求报告 R-003（房屋状态）。
 *
 * <p>状态取值与默认值都是纯逻辑，不需要数据库与界面，因此放在常规单测里。
 * 「成交自动置位」「不自动回退」这类跨表行为要连库验证，见定向探针。
 */
public final class HouseTest {

    private HouseTest() {
    }

    public static void run(TestRunner t) {
        t.suite("House · 状态取值（R-003）");

        t.equals("状态只有两种", 2, House.STATUSES.length);
        t.check("含「空置」", contains(House.STATUS_VACANT));
        t.check("含「已租出」", contains(House.STATUS_RENTED));

        t.check("「空置」是合法取值", House.isValidStatus(House.STATUS_VACANT));
        t.check("「已租出」是合法取值", House.isValidStatus(House.STATUS_RENTED));
        t.check("null 不是合法取值", !House.isValidStatus(null));
        t.check("空串不是合法取值", !House.isValidStatus(""));
        t.check("带空白的「空置」不算合法（不做 trim）", !House.isValidStatus(" 空置 "));
        t.check("未登记的取值不算合法（例如「已售出」）", !House.isValidStatus("已售出"));

        t.suite("House · 默认状态");

        t.equals("五参构造默认「空置」",
                House.STATUS_VACANT, withoutStatus().getStatus());
        t.equals("六参传 null 回落到「空置」",
                House.STATUS_VACANT, withStatus(null).getStatus());
        t.equals("六参传空串回落到「空置」",
                House.STATUS_VACANT, withStatus("").getStatus());
        t.equals("六参传「已租出」时如实保存",
                House.STATUS_RENTED, withStatus(House.STATUS_RENTED).getStatus());

        t.check("状态为已租出时 isRented 为真", withStatus(House.STATUS_RENTED).isRented());
        t.check("状态为空置时 isRented 为假", !withStatus(House.STATUS_VACANT).isRented());
    }

    // ---------------------------------------------------------------- 辅助

    private static boolean contains(String status) {
        for (String allowed : House.STATUSES) {
            if (allowed.equals(status)) {
                return true;
            }
        }
        return false;
    }

    /** 走五参构造：状态应落到默认值 */
    private static House withoutStatus() {
        return new House("H1", "两居", 88, "某路 1 号", landlord());
    }

    /** 走六参构造：状态按传入值处理 */
    private static House withStatus(String status) {
        return new House("H1", "两居", 88, "某路 1 号", landlord(), status);
    }

    private static Landlord landlord() {
        return new Landlord("L1", "张三", "13800138000");
    }
}
