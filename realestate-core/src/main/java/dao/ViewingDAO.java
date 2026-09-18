package dao;

import model.Viewing;
import util.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 带看记录的数据访问。对应需求报告 G-008。
 *
 * <p>查询时 JOIN 客户与房屋表，把客户姓名与房屋地址一并取出——列表要显示这两个
 * 人看得懂的信息，只给 ID 毫无意义。
 *
 * <p>时间字段用 {@code setObject(LocalDateTime)} / {@code getObject(..., LocalDateTime.class)}
 * 收发，而不是 Timestamp：后者会经过 JVM 默认时区换算，再叠加连接串里的
 * serverTimezone，容易出现「写进去和读出来差 8 小时」这类问题。
 * JDBC 4.2 的 LocalDateTime 映射是字面值直传，与时区无关。
 *
 * <p>对应 <b>R-003</b>：写入时若结果为「已成交」，在同一事务里把房屋置为「已租出」；
 * 反向（改回或删除）不动房屋状态。见 {@link #markHouseRentedIfDeal}。
 */
public class ViewingDAO {

    /**
     * R-003：带看结果若为「已成交」，需要与写入带看记录在<b>同一事务</b>里把房屋
     * 置为「已租出」。因业务动作的原子性落在这里，所以本 DAO 会调用同类中的
     * {@link HouseDAO#markRented}（由调用方传入连接）。
     */
    private final HouseDAO houseDAO = new HouseDAO();

    /** 列表与按 ID 查询共用的投影与连接，避免两处各写一遍列名 */
    private static final String SELECT_BASE =
            "SELECT v.id, v.customer_id, c.name AS customer_name, "
                    + "v.house_id, h.address AS house_address, "
                    + "v.viewed_at, v.result, v.note "
                    + "FROM viewings v "
                    + "JOIN customers c ON v.customer_id = c.id "
                    + "JOIN houses h ON v.house_id = h.id";

    private static final String SELECT_ALL_SQL =
            SELECT_BASE + " ORDER BY v.viewed_at DESC, v.id DESC";

    /** 按主键取单条。编辑时用它拿「原来的房屋」，判断房屋有没有被换过（G-020） */
    private static final String SELECT_BY_ID_SQL = SELECT_BASE + " WHERE v.id = ?";

    private static final String INSERT_SQL =
            "INSERT INTO viewings (customer_id, house_id, viewed_at, result, note) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE viewings SET customer_id = ?, house_id = ?, viewed_at = ?, "
                    + "result = ?, note = ? WHERE id = ?";

    private static final String DELETE_SQL = "DELETE FROM viewings WHERE id = ?";

    private static final String COUNT_BY_HOUSE_SQL =
            "SELECT COUNT(*) FROM viewings WHERE house_id = ?";

    private static final String COUNT_BY_CUSTOMER_SQL =
            "SELECT COUNT(*) FROM viewings WHERE customer_id = ?";

    public List<Viewing> getAll() {
        List<Viewing> viewings = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                viewings.add(read(rs));
            }
        } catch (SQLException e) {
            System.err.println("查询带看记录失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
        return viewings;
    }

    /**
     * 按主键取单条带看记录。
     *
     * <p>编辑带看时的前置校验需要知道「这条记录原来挂在哪套房上」——只有房子没被换过，
     * 才允许它继续挂在「已租出」的房源上（否则改个备注都会被拦下）。G-020
     *
     * @return 记录不存在时返回 {@code null}
     */
    public Viewing findById(long id) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("查询带看记录失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
    }

    /**
     * 新增带看记录。自增主键由数据库分配。
     *
     * <p>R-003：结果为「已成交」时，在同一事务里把该房屋置为「已租出」。
     * 同事务是必需的——否则可能出现「带看记录已写成已成交、房屋状态却没改」的
     * 不一致，界面上就会显示一套已成交却仍算空置的房子。
     */
    public boolean insert(Viewing viewing) {
        Connection conn = null;
        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            int affected;
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, viewing.getCustomerId());
                stmt.setString(2, viewing.getHouseId());
                stmt.setObject(3, viewing.getViewedAt());
                stmt.setString(4, viewing.getResult());
                stmt.setString(5, viewing.getNote());
                affected = stmt.executeUpdate();
            }

            markHouseRentedIfDeal(conn, viewing);

            conn.commit();
            System.out.println("新增带看记录成功，受影响行数: " + affected);
            return affected > 0;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            System.err.println("新增带看记录失败: " + e.getMessage());
            throw DataAccessException.from(e);
        } finally {
            closeQuietly(conn);
        }
    }

    /** 更新带看记录。R-003：结果为「已成交」时同事务置房屋为已租出，理由见 {@link #insert} */
    public boolean update(Viewing viewing) {
        Connection conn = null;
        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            int affected;
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, viewing.getCustomerId());
                stmt.setString(2, viewing.getHouseId());
                stmt.setObject(3, viewing.getViewedAt());
                stmt.setString(4, viewing.getResult());
                stmt.setString(5, viewing.getNote());
                stmt.setLong(6, viewing.getId());
                affected = stmt.executeUpdate();
            }

            markHouseRentedIfDeal(conn, viewing);

            conn.commit();
            return affected > 0;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            System.err.println("更新带看记录失败: " + e.getMessage());
            throw DataAccessException.from(e);
        } finally {
            closeQuietly(conn);
        }
    }

    public boolean delete(long id) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {

            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("删除带看记录失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
    }

    /** 某套房屋的带看记录条数。删除房屋前用它提示「将同时删除 N 条」 */
    public int countByHouse(String houseId) {
        return count(COUNT_BY_HOUSE_SQL, houseId);
    }

    /** 某位客户的带看记录条数。删除客户前用它提示「将同时删除 N 条」 */
    public int countByCustomer(String customerId) {
        return count(COUNT_BY_CUSTOMER_SQL, customerId);
    }

    private int count(String sql, String key) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /**
     * R-003：结果为「已成交」时把房屋置为「已租出」。
     *
     * <p>刻意<b>只往「占用」方向走</b>：把已成交改回其它结果、或删掉这条记录时，
     * 都不动房屋状态。理由是两种错误的代价不对称——把已租出当空置会导致重复推荐
     * （对客户失信），把空置当已租出只是少推一套。释放必须由人到房屋页确认。
     * 详见需求报告 4.3。
     */
    private void markHouseRentedIfDeal(Connection conn, Viewing viewing) throws SQLException {
        if (!Viewing.RESULT_DEAL.equals(viewing.getResult())) {
            return;
        }
        int affected = houseDAO.markRented(conn, viewing.getHouseId());
        System.out.println(affected > 0
                ? "房屋已置为「已租出」: " + viewing.getHouseId()
                : "房屋不存在，未改动状态: " + viewing.getHouseId());
    }

    private void rollbackQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            System.err.println("回滚事务失败: " + e.getMessage());
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            System.err.println("关闭连接失败: " + e.getMessage());
        }
    }

    private Viewing read(ResultSet rs) throws SQLException {
        LocalDateTime viewedAt = rs.getObject("viewed_at", LocalDateTime.class);
        return new Viewing(
                rs.getLong("id"),
                rs.getString("customer_id"),
                rs.getString("customer_name"),
                rs.getString("house_id"),
                rs.getString("house_address"),
                viewedAt,
                rs.getString("result"),
                rs.getString("note"));
    }
}
