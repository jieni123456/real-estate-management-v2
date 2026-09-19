package test;

import model.House;
import model.Landlord;
import util.HouseQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HouseQuery 的测试。对应需求报告 R-004 阶段 3。
 *
 * <p>为什么这条规则值得单独测：网页端的「导出 CSV」是服务端按同样的参数重算一遍，
 * 所以这份筛选规则同时决定「屏幕上看到几行」和「文件里有几行」。规则一旦漂移，
 * 用户会看到两个数字对不上，而且极难解释。这里把语义钉死。
 */
public final class HouseQueryTest {

    private HouseQueryTest() {
    }

    public static void run(TestRunner t) {
        t.suite("HouseQuery · 关键字（R-004 阶段 3）");

        t.equals("不带条件返回全部", "H1,H2,H3", ids(HouseQuery.filter(all(), null, null)));
        t.equals("空关键字与空状态也返回全部",
                "H1,H2,H3", ids(HouseQuery.filter(all(), "  ", "")));

        t.equals("按地址片段命中两行", "H1,H3", ids(HouseQuery.filter(all(), "阳光", null)));
        t.equals("按完整 ID 命中一行", "H2", ids(HouseQuery.filter(all(), "H2", null)));
        t.equals("ID 匹配大小写不敏感", "H2", ids(HouseQuery.filter(all(), "h2", null)));
        t.equals("按户型命中", "H2", ids(HouseQuery.filter(all(), "三居", null)));
        t.equals("按房东姓名命中两行（同一房东的两套房）",
                "H1,H3", ids(HouseQuery.filter(all(), "张三", null)));
        t.equals("按房东电话命中", "H2", ids(HouseQuery.filter(all(), "13900139000", null)));

        t.equals("多关键字需全部命中", "H3", ids(HouseQuery.filter(all(), "阳光 99", null)));
        t.equals("多关键字有一个不中则整体不匹配",
                "", ids(HouseQuery.filter(all(), "阳光 三居", null)));

        t.equals("面积不参与关键字匹配（搜 88 应为空）",
                "", ids(HouseQuery.filter(all(), "88", null)));

        t.suite("HouseQuery · 状态");

        t.equals("状态精确匹配「已租出」", "H2", ids(HouseQuery.filter(all(), null, "已租出")));
        t.equals("状态精确匹配「空置」", "H1,H3", ids(HouseQuery.filter(all(), null, "空置")));
        t.equals("状态两侧空白被忽略", "H2", ids(HouseQuery.filter(all(), null, " 已租出 ")));
        t.equals("未登记的取值筛不出任何行",
                "", ids(HouseQuery.filter(all(), null, "已售出")));

        t.suite("HouseQuery · 状态与关键字叠加");

        t.equals("空置 + 阳光", "H1,H3", ids(HouseQuery.filter(all(), "阳光", "空置")));
        t.equals("空置 + H2（H2 是已租出）",
                "", ids(HouseQuery.filter(all(), "H2", "空置")));
        t.equals("已租出 + 阳光", "", ids(HouseQuery.filter(all(), "阳光", "已租出")));

        t.suite("HouseQuery · 边界");

        t.equals("null 集合返回空列表", 0, HouseQuery.filter(null, "阳光", null).size());
        t.equals("空集合返回空列表", 0, HouseQuery.filter(List.of(), null, null).size());

        List<House> withNull = new ArrayList<>(all());
        withNull.add(null);
        t.equals("集合里的 null 元素被跳过，不影响其余结果",
                "H1,H2,H3", ids(HouseQuery.filter(withNull, null, null)));
    }

    // ---------------------------------------------------------------- 辅助

    /** 三套房屋：H1/H3 同属房东 L1 且都在阳光路，H2 单独一户且已租出 */
    private static List<House> all() {
        Landlord zhang = new Landlord("L1", "张三", "13800138000");
        Landlord li = new Landlord("L2", "李四", "13900139000");

        return Arrays.asList(
                new House("H1", "两居", 88, "阳光路 1 号", zhang, House.STATUS_VACANT),
                new House("H2", "三居", 120, "人民大道 22 号", li, House.STATUS_RENTED),
                new House("H3", "两居", 60, "阳光路 99 号", zhang, House.STATUS_VACANT));
    }

    private static String ids(List<House> houses) {
        StringBuilder builder = new StringBuilder();
        for (House house : houses) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(house.getId());
        }
        return builder.toString();
    }
}
