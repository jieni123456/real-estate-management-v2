package model;

import java.time.LocalDateTime;

/**
 * 一条带看记录。对应需求报告 G-008。
 *
 * <p>它是「客户 → 带看 → 成交」这条业务链的载体，也是 customers 与 houses
 * 两张表之间唯一的关联——在此之前两者完全孤立。
 *
 * <p>{@code customerName} 与 {@code houseAddress} 来自查询时的 JOIN，
 * 只为列表展示方便，不参与写入。
 */
public class Viewing {

    /** 带看结果的可选值。成交与否是这条记录最核心的信息 */
    public static final String RESULT_INTENT = "意向中";
    public static final String RESULT_DEAL = "已成交";
    public static final String RESULT_REJECT = "无意向";

    public static final String[] RESULTS = {RESULT_INTENT, RESULT_DEAL, RESULT_REJECT};

    /** 新增时的占位 ID：数据库自增，由数据库分配 */
    public static final long NEW_ID = 0L;

    private final long id;
    private final String customerId;
    private final String customerName;
    private final String houseId;
    private final String houseAddress;
    private final LocalDateTime viewedAt;
    private final String result;
    private final String note;

    public Viewing(long id, String customerId, String customerName,
                   String houseId, String houseAddress,
                   LocalDateTime viewedAt, String result, String note) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.houseId = houseId;
        this.houseAddress = houseAddress;
        this.viewedAt = viewedAt;
        this.result = result;
        this.note = note;
    }

    /** 结果取值是否合法。纯函数，便于单独验证 */
    public static boolean isValidResult(String value) {
        if (value == null) {
            return false;
        }
        for (String allowed : RESULTS) {
            if (allowed.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getHouseId() {
        return houseId;
    }

    public String getHouseAddress() {
        return houseAddress;
    }

    public LocalDateTime getViewedAt() {
        return viewedAt;
    }

    public String getResult() {
        return result;
    }

    public String getNote() {
        return note;
    }

    /** 是否已成交。供概览类统计使用 */
    public boolean isDeal() {
        return RESULT_DEAL.equals(result);
    }
}
