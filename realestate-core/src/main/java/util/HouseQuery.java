package util;

import model.House;
import model.Landlord;

import java.util.ArrayList;
import java.util.List;

/**
 * 房屋列表的筛选规则（关键字 + 状态）。对应需求报告 R-004 阶段 3。
 *
 * <p><b>为什么把它抽成 core 里的纯函数：</b>网页端的导出是「服务端重新算一遍」
 * 而不是「前端把屏幕上的行传回去」，这样导出内容与筛选结果的一致性由构造保证。
 * 而筛选规则必须是<b>一份定义</b>——否则界面按一套规则筛、导出按另一套筛，
 * 用户会看到「屏幕上有 3 行，导出的却是 5 行」这种极难解释的偏差。
 *
 * <p>字段范围与桌面版 {@code HouseView.applyFilter} 以及前端
 * {@code utils/search.js} 的调用点完全一致：ID / 户型 / 地址 / 房东ID / 房东姓名 /
 * 房东电话。<b>刻意不含面积</b>——面积是数值，用关键字模糊匹配没有意义。
 *
 * <p>不碰数据库、不碰界面，因此可以直接单测（见 test/HouseQueryTest）。
 */
public final class HouseQuery {

    private HouseQuery() {
    }

    /**
     * 过滤房屋列表。
     *
     * @param houses  待过滤的集合；为 {@code null} 时返回空列表
     * @param keyword 关键字，空格分隔多个词且需全部命中；空白表示不限
     * @param status  状态精确匹配；空白表示不限
     */
    public static List<House> filter(List<House> houses, String keyword, String status) {
        List<House> result = new ArrayList<>();
        if (houses == null) {
            return result;
        }

        String wanted = status == null ? "" : status.trim();

        for (House house : houses) {
            if (house == null) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.equals(house.getStatus())) {
                continue;
            }
            Landlord landlord = house.getLandlord();
            if (!SearchMatcher.matches(keyword,
                    house.getId(),
                    house.getType(),
                    house.getAddress(),
                    landlord == null ? "" : landlord.getId(),
                    landlord == null ? "" : landlord.getName(),
                    landlord == null ? "" : landlord.getContact())) {
                continue;
            }
            result.add(house);
        }
        return result;
    }
}
