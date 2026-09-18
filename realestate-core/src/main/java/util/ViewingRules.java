package util;

import model.House;

import java.util.ArrayList;
import java.util.List;

/**
 * 「哪些房源可以登记带看」这条业务规则（需求报告 G-020）。
 *
 * <p>抽成纯函数而不是散在界面与控制器里，有两个好处：界面用它过滤下拉、控制器用它
 * 做兜底拦截，两边共用同一个定义，不会各自漂移；也便于脱离数据库直接断言。
 *
 * <p>规则只有一句：只有「空置」的房源可以带看。已租出的房子继续带别人看，在现实里
 * 不会发生。要重新带看，得先在房屋管理里把状态改回「空置」——那条人工确认路径正好
 * 与 R-003「自动置位、不自动回退」的设计对齐。
 */
public final class ViewingRules {

    private ViewingRules() {
    }

    /** 该状态的房源是否可登记带看 */
    public static boolean isSelectable(String status) {
        return !House.STATUS_RENTED.equals(status);
    }

    /**
     * 可选的房源清单。
     *
     * @param all         全部房源
     * @param keepHouseId 即便已租出也允许保留的房源 ID。编辑一条旧记录时传它原来挂着的
     *                    房屋，否则房子后来租出去了就连改个备注都存不下来；新增时传 null
     */
    public static List<House> selectable(List<House> all, String keepHouseId) {
        List<House> result = new ArrayList<>();
        if (all == null) {
            return result;
        }
        for (House house : all) {
            if (isSelectable(house.getStatus()) || house.getId().equals(keepHouseId)) {
                result.add(house);
            }
        }
        return result;
    }

    /**
     * 不能登记时返回给用户看的原因，可登记时返回 null。
     *
     * @param status 房屋当前状态；房屋不存在时传 null
     */
    public static String blockReason(String houseId, String status) {
        if (status == null) {
            return "房屋ID「" + houseId + "」不存在，请从下拉列表中选择";
        }
        if (House.STATUS_RENTED.equals(status)) {
            return "房屋「" + houseId + "」已租出，不能再登记带看。"
                    + "如需重新带看，请先到「房屋管理」把它改回「空置」。";
        }
        return null;
    }
}
