package model;

/**
 * 一套房屋。对应需求报告 R-003：状态（空置 / 已租出）是房屋自己的属性，
 * 带看成交只是「触发它改变的一个事件」，而不是计算它的依据。
 *
 * <p>之所以不由带看记录派生：带看记录对客户是 {@code ON DELETE CASCADE}，
 * 删掉一个客户会连带删掉他的带看记录，派生方案下已租出的房子会静默变回空置。
 * 详见需求报告第四章 4.2。
 */
public class House {

    /** 房屋状态的可选值。集中定义，避免在多个文件里散写字符串字面量 */
    public static final String STATUS_VACANT = "空置";
    public static final String STATUS_RENTED = "已租出";

    public static final String[] STATUSES = {STATUS_VACANT, STATUS_RENTED};

    private String id;
    private String type;
    private double area;
    private String address;
    private Landlord landlord;
    private String status;

    /** 新增但未指定状态时默认「空置」 */
    public House(String id, String type, double area, String address, Landlord landlord) {
        this(id, type, area, address, landlord, STATUS_VACANT);
    }

    public House(String id, String type, double area, String address,
                 Landlord landlord, String status) {
        this.id = id;
        this.type = type;
        this.area = area;
        this.address = address;
        this.landlord = landlord;
        this.status = status == null || status.isEmpty() ? STATUS_VACANT : status;
    }

    /** 状态取值是否合法。纯函数，便于单独验证 */
    public static boolean isValidStatus(String value) {
        if (value == null) {
            return false;
        }
        for (String allowed : STATUSES) {
            if (allowed.equals(value)) {
                return true;
            }
        }
        return false;
    }

    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public double getArea() { return area; }
    public String getAddress() { return address; }
    public Landlord getLandlord() { return landlord; }
    public String getStatus() { return status; }

    /** 是否已租出。供概览统计与界面上的状态标签使用 */
    public boolean isRented() { return STATUS_RENTED.equals(status); }
}
