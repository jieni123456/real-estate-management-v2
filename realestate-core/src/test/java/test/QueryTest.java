package test;

import model.Customer;
import model.Viewing;
import util.CustomerQuery;
import util.ViewingQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CustomerQuery 与 ViewingQuery 的测试。对应需求报告 R-004 阶段 4。
 *
 * <p>为什么这两条规则值得单独测：网页端的「导出 CSV」是服务端按同样的参数重算一遍，
 * 所以筛选规则同时决定「屏幕上看到几行」和「文件里有几行」。规则一旦漂移，
 * 用户会看到两个数字对不上，而且极难解释。
 *
 * <p>尤其要钉住「哪些字段参与匹配」：桌面端 {@code CustomerView} / {@code ViewingView}
 * 的 applyFilter 与这里是同一套字段范围，多一个字段或少一个字段都算漂移。
 */
public final class QueryTest {

    private QueryTest() {
    }

    public static void run(TestRunner t) {
        customerKeyword(t);
        viewingKeywordAndResult(t);
    }

    // ---------------------------------------------------------------- 客户

    private static void customerKeyword(TestRunner t) {
        t.suite("CustomerQuery · 关键字（R-004 阶段 4）");

        t.equals("不带条件返回全部", "C1,C2,C3", customerIds(CustomerQuery.filter(customers(), null)));
        t.equals("空白关键字返回全部",
                "C1,C2,C3", customerIds(CustomerQuery.filter(customers(), "   ")));

        t.equals("按姓名命中一行", "C1", customerIds(CustomerQuery.filter(customers(), "张三")));
        t.equals("按电话命中一行", "C2", customerIds(CustomerQuery.filter(customers(), "13900139000")));
        t.equals("按需求描述命中两行",
                "C1,C3", customerIds(CustomerQuery.filter(customers(), "要")));

        t.equals("ID 匹配大小写不敏感", "C2", customerIds(CustomerQuery.filter(customers(), "c2")));

        t.equals("多关键字需全部命中",
                "C3", customerIds(CustomerQuery.filter(customers(), "王五 三居")));
        t.equals("多关键字有一个不中则整体不匹配",
                "", customerIds(CustomerQuery.filter(customers(), "王五 两居")));

        t.suite("CustomerQuery · 边界");

        t.equals("null 集合返回空列表", 0, CustomerQuery.filter(null, "张三").size());
        t.equals("空集合返回空列表", 0, CustomerQuery.filter(List.of(), null).size());

        List<Customer> withNull = new ArrayList<>(customers());
        withNull.add(null);
        t.equals("集合里的 null 元素被跳过，不影响其余结果",
                "C1,C2,C3", customerIds(CustomerQuery.filter(withNull, null)));
    }

    // ---------------------------------------------------------------- 带看

    private static void viewingKeywordAndResult(TestRunner t) {
        t.suite("ViewingQuery · 关键字（R-004 阶段 4）");

        t.equals("不带条件返回全部", "V1,V2,V3", viewingIds(ViewingQuery.filter(viewings(), null, null)));
        t.equals("空白关键字与空白结果也返回全部",
                "V1,V2,V3", viewingIds(ViewingQuery.filter(viewings(), "  ", "")));

        t.equals("按客户姓名命中两行（同一客户的两条记录）",
                "V1,V3", viewingIds(ViewingQuery.filter(viewings(), "张三", null)));
        t.equals("按客户ID命中", "V2", viewingIds(ViewingQuery.filter(viewings(), "C2", null)));
        t.equals("按地址片段命中两行",
                "V1,V3", viewingIds(ViewingQuery.filter(viewings(), "阳光", null)));
        t.equals("按备注命中（英文大小写不敏感）",
                "V3", viewingIds(ViewingQuery.filter(viewings(), "expensive", null)));

        /* 关键字取「张三 + 采光」而不是「张三 + 阳光路 1 号」：后者里那个孤立的
         * "1" 会命中 V3 的客户ID「C1」——「包含即命中」的语义下这是**正确行为**
         * （用户搜 1 本来就该得到所有含 1 的记录，桌面端也一样），
         * 但会让这条断言测不出它想测的东西。写筛选类断言时，
         * 关键字的每个词都要先确认「它还可能命中谁」。 */
        t.equals("多关键字需全部命中",
                "V1", viewingIds(ViewingQuery.filter(viewings(), "张三 采光", null)));
        t.equals("多关键字有一个不中则整体不匹配",
                "", viewingIds(ViewingQuery.filter(viewings(), "张三 人民大道", null)));

        /* 时间不参与关键字匹配 —— 与桌面端 ViewingView.applyFilter 一致。
         * 时间是格式化过的数值，用关键字模糊匹配只会带来「搜 9 把 9 月的全捞出来」
         * 这类意外结果。 */
        t.equals("带看时间不参与关键字匹配（搜 2026 应为空）",
                "", viewingIds(ViewingQuery.filter(viewings(), "2026", null)));

        t.suite("ViewingQuery · 结果筛选");

        t.equals("结果精确匹配「已成交」", "V2", viewingIds(ViewingQuery.filter(viewings(), null, "已成交")));
        t.equals("结果精确匹配「意向中」", "V1", viewingIds(ViewingQuery.filter(viewings(), null, "意向中")));
        t.equals("结果两侧空白被忽略",
                "V2", viewingIds(ViewingQuery.filter(viewings(), null, " 已成交 ")));
        t.equals("未登记的取值筛不出任何行",
                "", viewingIds(ViewingQuery.filter(viewings(), null, "已取消")));

        t.suite("ViewingQuery · 结果与关键字叠加");

        t.equals("意向中 + 张三", "V1", viewingIds(ViewingQuery.filter(viewings(), "张三", "意向中")));
        t.equals("已成交 + 张三（V2 的客户是李四）",
                "", viewingIds(ViewingQuery.filter(viewings(), "张三", "已成交")));
        t.equals("已成交 + 人民大道", "V2", viewingIds(ViewingQuery.filter(viewings(), "人民大道", "已成交")));

        t.suite("ViewingQuery · 边界");

        t.equals("null 集合返回空列表", 0, ViewingQuery.filter(null, "张三", null).size());
        t.equals("空集合返回空列表", 0, ViewingQuery.filter(List.of(), null, null).size());

        List<Viewing> withNull = new ArrayList<>(viewings());
        withNull.add(null);
        t.equals("集合里的 null 元素被跳过，不影响其余结果",
                "V1,V2,V3", viewingIds(ViewingQuery.filter(withNull, null, null)));
    }

    // ---------------------------------------------------------------- 辅助

    /** 两位能被「要」字命中的客户 + 一位不能的 */
    private static List<Customer> customers() {
        return Arrays.asList(
                new Customer("C1", "张三", "13800138000", "要两居，预算 3000"),
                new Customer("C2", "李四", "13900139000", ""),
                new Customer("C3", "王五", "13700137000", "要三居，近地铁"));
    }

    /**
     * 三条带看：V1/V3 同属客户张三、都在阳光路；V2 是李四看人民大道且已成交。
     * 结果刻意覆盖三态，好让「结果筛选」这一段有区分度。
     */
    private static List<Viewing> viewings() {
        return Arrays.asList(
                new Viewing(1, "C1", "张三", "H1", "阳光路 1 号",
                        LocalDateTime.of(2026, 9, 1, 10, 0), Viewing.RESULT_INTENT, "看看采光"),
                new Viewing(2, "C2", "李四", "H2", "人民大道 22 号",
                        LocalDateTime.of(2026, 9, 2, 11, 0), Viewing.RESULT_DEAL, ""),
                new Viewing(3, "C1", "张三", "H3", "阳光路 99 号",
                        LocalDateTime.of(2026, 9, 3, 12, 0), Viewing.RESULT_REJECT, "too expensive"));
    }

    private static String customerIds(List<Customer> customers) {
        StringBuilder builder = new StringBuilder();
        for (Customer customer : customers) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(customer.getId());
        }
        return builder.toString();
    }

    /** 带看记录没有业务编号，用自增 id 拼成序号（V1/V2/V3）便于断言 */
    private static String viewingIds(List<Viewing> viewings) {
        StringBuilder builder = new StringBuilder();
        for (Viewing viewing : viewings) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append('V').append(viewing.getId());
        }
        return builder.toString();
    }
}
