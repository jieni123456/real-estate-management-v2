package dao;

import model.House;
import model.Overview;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 统计查询。对应需求报告 G-009（概览页）。
 *
 * <p>全部查询共用一条连接，避免为一个页面反复建立连接。
 * 这些语句都是只读聚合，不涉及事务。
 *
 * <p>R-003：增加「空置房源」计数（原「平均面积」已从概览页移除）。
 */
public class StatsDAO {

    private static final String COUNT_HOUSES = "SELECT COUNT(*) FROM houses";
    private static final String COUNT_CUSTOMERS = "SELECT COUNT(*) FROM customers";
    private static final String COUNT_LANDLORDS = "SELECT COUNT(*) FROM landlords";
    private static final String COUNT_VIEWINGS = "SELECT COUNT(*) FROM viewings";

    /**
     * 按状态数房源（R-003）。
     * 状态用参数传入，而不是在 SQL 里写死中文字面量——取值只在 {@link House} 里定义一份。
     */
    private static final String COUNT_HOUSES_BY_STATUS =
            "SELECT COUNT(*) FROM houses WHERE status = ?";

    private static final String TYPE_DISTRIBUTION =
            "SELECT type, COUNT(*) AS total FROM houses GROUP BY type ORDER BY total DESC, type";

    /** 一次性取回概览页所需的全部统计值 */
    public Overview loadOverview() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            int houseCount = count(conn, COUNT_HOUSES);
            int customerCount = count(conn, COUNT_CUSTOMERS);
            int landlordCount = count(conn, COUNT_LANDLORDS);
            int viewingCount = count(conn, COUNT_VIEWINGS);
            int vacantCount = countHousesByStatus(conn, House.STATUS_VACANT);
            List<Overview.TypeCount> typeCounts = typeDistribution(conn);

            return new Overview(houseCount, customerCount, landlordCount, viewingCount,
                    vacantCount, typeCounts);

        } catch (SQLException e) {
            System.err.println("统计查询失败: " + e.getMessage());
            e.printStackTrace();
            return Overview.empty();
        }
    }

    private int count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 某状态的房源数（R-003） */
    private int countHousesByStatus(Connection conn, String status) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(COUNT_HOUSES_BY_STATUS)) {
            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<Overview.TypeCount> typeDistribution(Connection conn) throws SQLException {
        List<Overview.TypeCount> result = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(TYPE_DISTRIBUTION);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(new Overview.TypeCount(rs.getString("type"), rs.getInt("total")));
            }
        }
        return result;
    }
}
