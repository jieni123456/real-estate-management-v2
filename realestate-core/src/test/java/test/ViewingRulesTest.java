package test;

import model.House;
import model.Landlord;
import util.ViewingRules;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewingRules 的测试。对应需求报告 G-020（已租出的房源不能登记带看）。
 *
 * <p>这条规则界面与控制器都要用，抽成纯函数后就能脱离数据库直接断言。
 * 真正「拦住写入」的效果要连库验证，见定向探针。
 */
public final class ViewingRulesTest {

    private ViewingRulesTest() {
    }

    public static void run(TestRunner t) {
        t.suite("ViewingRules · 可带看判断（G-020）");

        t.check("空置房源可带看", ViewingRules.isSelectable(House.STATUS_VACANT));
        t.check("已租出房源不可带看", !ViewingRules.isSelectable(House.STATUS_RENTED));
        t.check("状态为 null 时按可带看处理（与 House 的默认值一致）",
                ViewingRules.isSelectable(null));

        t.suite("ViewingRules · 下拉候选清单");

        List<House> all = sample();
        t.equals("只留空置房源", 2, ViewingRules.selectable(all, null).size());
        t.check("已租出的不在其中", !contains(ViewingRules.selectable(all, null), "R1"));
        t.check("空置的都在其中", contains(ViewingRules.selectable(all, null), "V1")
                && contains(ViewingRules.selectable(all, null), "V2"));

        t.equals("编辑时可保留原房屋（已租出）", 3,
                ViewingRules.selectable(all, "R1").size());
        t.check("保留的是传入的那一套",
                contains(ViewingRules.selectable(all, "R1"), "R1"));

        t.equals("传入的 ID 不在列表中时不会凭空多出", 2,
                ViewingRules.selectable(all, "不存在").size());

        t.check("返回的是新列表而非入参本身", ViewingRules.selectable(all, null) != all);
        t.check("入参为 null 时返回空列表且不抛异常",
                ViewingRules.selectable(null, null).isEmpty());
        t.check("入参为空列表时返回空列表",
                ViewingRules.selectable(new ArrayList<>(), "R1").isEmpty());
        t.equals("不修改入参列表", 3, all.size());

        t.suite("ViewingRules · 拒绝原因");

        String blocked = ViewingRules.blockReason("101", House.STATUS_RENTED);
        t.check("已租出会给出原因", blocked != null);
        t.check("原因里带房屋 ID", blocked != null && blocked.contains("101"));
        t.check("原因说明是已租出", blocked != null && blocked.contains(House.STATUS_RENTED));
        t.check("原因给了下一步：改回空置",
                blocked != null && blocked.contains(House.STATUS_VACANT));

        t.check("空置房源不拦", ViewingRules.blockReason("22", House.STATUS_VACANT) == null);

        String missing = ViewingRules.blockReason("404", null);
        t.check("房屋不存在也会给出原因", missing != null);
        t.check("原因里带房屋 ID", missing != null && missing.contains("404"));
    }

    // ---------------------------------------------------------------- 辅助

    /** V1 / V2 空置，R1 已租出 */
    private static List<House> sample() {
        List<House> list = new ArrayList<>();
        list.add(house("V1", House.STATUS_VACANT));
        list.add(house("R1", House.STATUS_RENTED));
        list.add(house("V2", House.STATUS_VACANT));
        return list;
    }

    private static House house(String id, String status) {
        return new House(id, "两居", 88, "某路 " + id + " 号",
                new Landlord("L1", "张三", "13800138000"), status);
    }

    private static boolean contains(List<House> houses, String id) {
        for (House house : houses) {
            if (house.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
