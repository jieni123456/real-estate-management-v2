package model;

import java.util.List;

/**
 * 概览页所需的统计数据。对应需求报告 G-009。
 *
 * <p>说明：「最近添加」这一项在原始建议里出现过，但表结构中没有创建时间列
 * （houses / customers 只有业务字段），取不到真实值。与其用一个假的数据充数，
 * 这里改为展示可由现有字段算出的指标。若日后确实需要「最近添加」，
 * 应给表加 created_at 列并另行登记为需求。
 *
 * <p>阶段五新增「带看次数」（G-008 落地后才有这张表）。
 * R-003 把「平均面积」换成了「空置房源」——空置数是这个系统里最接近「库存」的指标，
 * 而平均面积的参考价值相对最低。
 */
public class Overview {

    private final int houseCount;
    private final int customerCount;
    private final int landlordCount;
    private final int viewingCount;
    private final int vacantCount;
    private final List<TypeCount> typeCounts;

    public Overview(int houseCount, int customerCount, int landlordCount, int viewingCount,
                    int vacantCount, List<TypeCount> typeCounts) {
        this.houseCount = houseCount;
        this.customerCount = customerCount;
        this.landlordCount = landlordCount;
        this.viewingCount = viewingCount;
        this.vacantCount = vacantCount;
        this.typeCounts = typeCounts == null ? List.of() : typeCounts;
    }

    /** 数据库不可用或查询失败时的空统计，避免界面出现 null 判断 */
    public static Overview empty() {
        return new Overview(0, 0, 0, 0, 0, List.of());
    }

    public int getHouseCount() {
        return houseCount;
    }

    public int getCustomerCount() {
        return customerCount;
    }

    public int getLandlordCount() {
        return landlordCount;
    }

    /** 带看记录总数（G-008） */
    public int getViewingCount() {
        return viewingCount;
    }

    /** 当前空置（未租出）的房源数（R-003） */
    public int getVacantCount() {
        return vacantCount;
    }

    /** 各户型的房源数，按数量降序 */
    public List<TypeCount> getTypeCounts() {
        return typeCounts;
    }

    /** 单个户型的房源数 */
    public static class TypeCount {

        private final String type;
        private final int count;

        public TypeCount(String type, int count) {
            this.type = type;
            this.count = count;
        }

        public String getType() {
            return type;
        }

        public int getCount() {
            return count;
        }
    }
}
