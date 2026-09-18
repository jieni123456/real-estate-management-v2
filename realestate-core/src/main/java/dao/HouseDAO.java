package dao;

import model.House;
import model.Landlord;
import util.DataAccessException;
import util.SecurityUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 房屋与房东的数据访问。
 *
 * <p>对应需求报告：
 * <ul>
 *   <li>G-013  返回强类型 {@link House}，不再返回 {@code Object[]}</li>
 *   <li>G-001  新增走纯 INSERT，ID 冲突即失败，<b>不再静默覆盖</b>已有记录</li>
 *   <li>G-010  房东与房屋两条写入包在同一事务中，避免出现孤儿数据</li>
 *   <li>G-012  SQL 异常不再被吞掉，改为抛出已归类的 {@link DataAccessException}，
 *       由 Controller 转成用户能看懂的说明</li>
 *   <li>G-018  删除房屋、或编辑时把房屋改挂到别的房东名下之后，若原房东已无任何
 *       房屋引用，在同一事务里一并清理——否则会留下界面上看不见、却一直躺在库里的
 *       孤儿房东记录</li>
 *   <li>R-003  房屋状态（空置 / 已租出）随房屋一起读写；
 *       {@link #markRented} 供「带看成交」在同一事务里改状态，
 *       {@link #updateStatus} 供房屋编辑里手改</li>
 * </ul>
 *
 * <p><b>房东信息的处理原则：INSERT IGNORE——不存在则创建，已存在则沿用原信息，
 * 绝不覆盖。</b>因为一个房东可能关联多套房屋，凭一次表单提交改写房东资料会连带
 * 影响其它房屋显示出来的房东信息。（房东的独立维护见缺口 G-007。）
 */
public class HouseDAO {

    private static final String LANDLORD_SQL =
            "INSERT IGNORE INTO landlords (id, name, encrypted_contact) VALUES (?, ?, ?)";

    private static final String INSERT_HOUSE_SQL =
            "INSERT INTO houses (id, type, area, address, landlord_id, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_HOUSE_SQL =
            "UPDATE houses SET type = ?, area = ?, address = ?, landlord_id = ?, status = ? "
                    + "WHERE id = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT h.id, h.type, h.area, h.address, h.status, "
                    + "l.id AS landlord_id, l.name AS landlord_name, l.encrypted_contact "
                    + "FROM houses h JOIN landlords l ON h.landlord_id = l.id "
                    + "ORDER BY h.id";

    /** 单独更新房屋状态（房屋编辑里手改，含退租）。R-003 */
    private static final String UPDATE_STATUS_SQL =
            "UPDATE houses SET status = ? WHERE id = ?";

    /**
     * 全部房东。供「添加 / 编辑房屋」对话框的下拉选择使用（G-007）。
     * 联系方式存在加密列里，取出后解密。
     */
    private static final String SELECT_LANDLORDS_SQL =
            "SELECT id, name, encrypted_contact FROM landlords ORDER BY id";

    /**
     * 房屋所属的房东 ID。
     * 删除房屋、或编辑时改房东之前，先把原房东记下来，用于判断它是否变成孤儿（G-018）。
     */
    private static final String SELECT_LANDLORD_OF_HOUSE_SQL =
            "SELECT landlord_id FROM houses WHERE id = ?";

    private static final String DELETE_HOUSE_SQL = "DELETE FROM houses WHERE id = ?";

    /** 只取状态。见 {@link #findStatus} */
    private static final String SELECT_STATUS_SQL = "SELECT status FROM houses WHERE id = ?";

    /**
     * 删除「已无任何房屋引用」的房东（G-018）。
     *
     * <p>判断条件直接写进 DELETE 语句本身（{@code NOT EXISTS}），而不是先查数量、
     * 再由 Java 决定删不删——后者在两步之间留出一个竞态窗口，前者由数据库在
     * 一条语句内原子完成。
     */
    private static final String DELETE_ORPHAN_LANDLORD_SQL =
            "DELETE FROM landlords WHERE id = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM houses WHERE landlord_id = landlords.id)";

    /** 该房东名下的房屋数量。界面用它预告「删这套房会不会顺手删掉房东」（G-018） */
    private static final String COUNT_HOUSES_OF_LANDLORD_SQL =
            "SELECT COUNT(*) FROM houses WHERE landlord_id = ?";

    /**
     * 只取房屋状态，不读整行。
     *
     * <p>供「已租出的房子不能再登记带看」这条规则判断（需求报告 G-020）使用。
     * 单独查询而不是复用 {@link #getAllHouses()}：登记带看是写操作的前置校验，
     * 没必要把全部房屋连同房东联系方式一起查出来再解密。
     *
     * @return 房屋不存在时返回 {@code null}
     */
    public String findStatus(String houseId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_STATUS_SQL)) {

            stmt.setString(1, houseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            System.err.println("查询房屋状态失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
    }

    /** 房屋 ID 是否已存在。供「新增 / 编辑」区分与冲突提示使用 */
    public boolean exists(String houseId) {
        String sql = "SELECT 1 FROM houses WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, houseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** 房东 ID 是否已存在。用于在成功提示里区分「新建房东」与「沿用已有房东」 */
    public boolean landlordExists(String landlordId) {
        String sql = "SELECT 1 FROM landlords WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, landlordId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** 新增房屋。ID 已存在时返回 false，不修改原记录 */
    public boolean insertHouse(House house) {
        return write(house, false);
    }

    /** 更新房屋。记录不存在时返回 false */
    public boolean updateHouse(House house) {
        return write(house, true);
    }

    private boolean write(House house, boolean update) {
        Connection conn = null;
        try {
            conn = DatabaseUtil.getConnection();
            // G-010：房东与房屋两条写入必须同生共死。
            // 否则第二条失败时第一条已提交，库里会留下「有房东、无房屋」的孤儿数据。
            conn.setAutoCommit(false);

            // G-018：编辑时先记下原房东。若本次把房屋改挂到别的房东名下，
            // 原房东可能就此失去最后一个引用，需要在同一事务里清掉。
            String previousLandlordId = update ? findLandlordId(conn, house.getId()) : null;

            try (PreparedStatement landlordStmt = conn.prepareStatement(LANDLORD_SQL)) {
                landlordStmt.setString(1, house.getLandlord().getId());
                landlordStmt.setString(2, house.getLandlord().getName());
                landlordStmt.setString(3,
                        SecurityUtil.encryptContact(house.getLandlord().getContact()));
                landlordStmt.executeUpdate();
            }

            int affected;
            try (PreparedStatement houseStmt =
                         conn.prepareStatement(update ? UPDATE_HOUSE_SQL : INSERT_HOUSE_SQL)) {
                if (update) {
                    houseStmt.setString(1, house.getType());
                    houseStmt.setDouble(2, house.getArea());
                    houseStmt.setString(3, house.getAddress());
                    houseStmt.setString(4, house.getLandlord().getId());
                    houseStmt.setString(5, house.getStatus());
                    houseStmt.setString(6, house.getId());
                } else {
                    houseStmt.setString(1, house.getId());
                    houseStmt.setString(2, house.getType());
                    houseStmt.setDouble(3, house.getArea());
                    houseStmt.setString(4, house.getAddress());
                    houseStmt.setString(5, house.getLandlord().getId());
                    houseStmt.setString(6, house.getStatus());
                }
                affected = houseStmt.executeUpdate();
            }

            // G-018：改房东后，原房东若已无任何房屋引用，一并清理。
            // 注意只在「换了房东」时才检查，否则每次编辑都要多跑一条 DELETE。
            if (previousLandlordId != null
                    && !previousLandlordId.equals(house.getLandlord().getId())) {
                int removed = deleteLandlordIfOrphan(conn, previousLandlordId);
                if (removed > 0) {
                    System.out.println("原房东已无房屋引用，一并清理: " + previousLandlordId);
                }
            }

            conn.commit();
            System.out.println((update ? "更新" : "新增") + "房屋成功: " + house.getId());
            return affected > 0;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            System.err.println((update ? "更新" : "新增") + "房屋失败: " + e.getMessage());
            throw DataAccessException.from(e);
        } finally {
            closeQuietly(conn);
        }
    }

    public List<House> getAllHouses() {
        List<House> houses = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Landlord landlord = new Landlord(
                        rs.getString("landlord_id"),
                        rs.getString("landlord_name"),
                        SecurityUtil.decryptContact(rs.getString("encrypted_contact")));

                houses.add(new House(
                        rs.getString("id"),
                        rs.getString("type"),
                        rs.getDouble("area"),
                        rs.getString("address"),
                        landlord,
                        rs.getString("status")));
            }
        } catch (SQLException e) {
            System.err.println("查询房屋列表失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
        return houses;
    }

    /** 全部房东列表，供房屋对话框的下拉选择使用（G-007） */
    public List<Landlord> getAllLandlords() {
        List<Landlord> landlords = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_LANDLORDS_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                landlords.add(new Landlord(
                        rs.getString("id"),
                        rs.getString("name"),
                        SecurityUtil.decryptContact(rs.getString("encrypted_contact"))));
            }
        } catch (SQLException e) {
            System.err.println("查询房东列表失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
        return landlords;
    }

    /**
     * 删除房屋（G-018）。
     *
     * <p>整个动作在一个事务里完成三件事：记下该房屋的房东 → 删除房屋 → 该房东若已无
     * 任何房屋引用则一并删除。放在同一事务是必要的：若删房东失败而删房屋已提交，
     * 就留下了本次要消除的孤儿记录。
     *
     * <p>房屋之下的带看记录由数据库外键 {@code ON DELETE CASCADE} 自动清除，
     * 无需在此处理（见 G-008）。
     *
     * @return 房屋不存在（可能是别人已删）时返回 false
     */
    public boolean deleteHouse(String houseId) {
        Connection conn = null;
        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            String landlordId = findLandlordId(conn, houseId);
            if (landlordId == null) {
                conn.rollback();
                return false;
            }

            int affected;
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_HOUSE_SQL)) {
                stmt.setString(1, houseId);
                affected = stmt.executeUpdate();
            }

            int removedLandlords = deleteLandlordIfOrphan(conn, landlordId);

            conn.commit();
            System.out.println("删除房屋成功: " + houseId
                    + (removedLandlords > 0 ? "（该房东已无其它房屋，一并清理）" : ""));
            return affected > 0;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            System.err.println("删除房屋失败: " + e.getMessage());
            throw DataAccessException.from(e);
        } finally {
            closeQuietly(conn);
        }
    }

    /** 该房东名下的房屋数量（G-018）。界面据此预告删除会连带清理房东 */
    public int countHousesByLandlord(String landlordId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(COUNT_HOUSES_OF_LANDLORD_SQL)) {

            stmt.setString(1, landlordId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("统计房东名下房屋数失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
    }

    // ------------------------------------------------------------ 房屋状态

    /**
     * 在<b>调用方的事务里</b>把房屋置为「已租出」（R-003）。
     *
     * <p>刻意接收 {@link Connection} 而不是自己开连接：登记一条「已成交」的带看记录
     * 与把房屋置为已租出必须同生共死，否则会出现「带看记录已写成已成交、房屋状态
     * 却没改」的不一致。事务由调用方提交或回滚，本方法既不提交也不关闭连接。
     *
     * <p>手工改状态（含退租）走的是 {@link #updateHouse}——编辑对话框保存时整行
     * 一起写，状态是其中一列，因此不需要单独的状态更新入口。
     *
     * @return 受影响行数；房屋不存在时为 0
     */
    public int markRented(Connection conn, String houseId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_STATUS_SQL)) {
            stmt.setString(1, House.STATUS_RENTED);
            stmt.setString(2, houseId);
            return stmt.executeUpdate();
        }
    }

    // ------------------------------------------------------------ 房东辅助

    /** 房屋所属的房东 ID。房屋不存在时返回 null */
    private String findLandlordId(Connection conn, String houseId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_LANDLORD_OF_HOUSE_SQL)) {
            stmt.setString(1, houseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * 该房东若已无任何房屋引用则删除它（G-018）。
     * 判断由 SQL 的 {@code NOT EXISTS} 完成，因此「刚查完就被别的房屋挂上」这种
     * 情况不会误删。
     *
     * @return 实际删除的行数；仍被其它房屋引用时为 0
     */
    private int deleteLandlordIfOrphan(Connection conn, String landlordId) throws SQLException {
        if (landlordId == null) {
            return 0;
        }
        try (PreparedStatement stmt = conn.prepareStatement(DELETE_ORPHAN_LANDLORD_SQL)) {
            stmt.setString(1, landlordId);
            return stmt.executeUpdate();
        }
    }

    // ------------------------------------------------------------ 事务辅助

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
}
