package util;

import model.Viewing;

import java.util.ArrayList;
import java.util.List;

/**
 * 带看记录的筛选规则（关键字 + 结果）。对应需求报告 R-004 阶段 4。
 *
 * <p>与 {@link HouseQuery} / {@link CustomerQuery} 同一套道理：导出的内容由服务端
 * 按与界面相同的条件重算，因此筛选规则只能有一份定义。
 *
 * <p>字段范围与桌面版 {@code ViewingView.applyFilter} 一致：客户ID / 客户姓名 /
 * 房屋ID / 地址 / 结果 / 备注。<b>故意不含带看时间</b>——时间是格式化的数值，
 * 用关键字模糊匹配没有意义（桌面端也是这么做的）。
 *
 * <p>结果筛选是相对桌面端的<u>增强</u>：桌面版的带看页只有关键字搜索，
 * 而房屋页有状态筛选。网页版让两者对称，属于纯加法。
 */
public final class ViewingQuery {

    private ViewingQuery() {
    }

    /**
     * 过滤带看记录。
     *
     * @param viewings 待过滤的集合；为 {@code null} 时返回空列表
     * @param keyword  关键字，空格分隔多个词且需全部命中；空白表示不限
     * @param result   带看结果精确匹配；空白表示不限
     */
    public static List<Viewing> filter(List<Viewing> viewings, String keyword, String result) {
        List<Viewing> filtered = new ArrayList<>();
        if (viewings == null) {
            return filtered;
        }

        String wanted = result == null ? "" : result.trim();

        for (Viewing viewing : viewings) {
            if (viewing == null) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.equals(viewing.getResult())) {
                continue;
            }
            if (!SearchMatcher.matches(keyword,
                    viewing.getCustomerId(), viewing.getCustomerName(),
                    viewing.getHouseId(), viewing.getHouseAddress(),
                    viewing.getResult(), viewing.getNote())) {
                continue;
            }
            filtered.add(viewing);
        }
        return filtered;
    }
}
