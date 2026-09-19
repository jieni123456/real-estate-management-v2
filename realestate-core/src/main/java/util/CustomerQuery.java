package util;

import model.Customer;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户列表的筛选规则。对应需求报告 R-004 阶段 4。
 *
 * <p>与 {@link HouseQuery} 同一套道理：网页端的导出是「服务端按同样的条件重算一遍」，
 * 而不是「前端把屏幕上的行传回去」，这样「导出的就是屏幕上看到的」由构造保证。
 * 筛选规则因此必须是<u>一份定义</u>，否则界面按一套筛、导出按另一套筛，
 * 用户会看到「屏幕上 3 行、文件里 5 行」这种极难解释的偏差。
 *
 * <p>字段范围与桌面版 {@code CustomerView.applyFilter} 完全一致：
 * ID / 姓名 / 电话 / 需求描述。
 *
 * <p>不碰数据库、不碰界面，因此可以直接单测。
 */
public final class CustomerQuery {

    private CustomerQuery() {
    }

    /**
     * 过滤客户列表。
     *
     * @param customers 待过滤的集合；为 {@code null} 时返回空列表
     * @param keyword   关键字，空格分隔多个词且需全部命中；空白表示不限
     */
    public static List<Customer> filter(List<Customer> customers, String keyword) {
        List<Customer> result = new ArrayList<>();
        if (customers == null) {
            return result;
        }

        for (Customer customer : customers) {
            if (customer == null) {
                continue;
            }
            if (!SearchMatcher.matches(keyword,
                    customer.getId(), customer.getName(),
                    customer.getPhone(), customer.getRequirements())) {
                continue;
            }
            result.add(customer);
        }
        return result;
    }
}
