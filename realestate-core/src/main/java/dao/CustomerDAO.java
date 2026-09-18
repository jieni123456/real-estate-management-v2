package dao;

import model.Customer;
import util.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户的数据访问。
 *
 * <p>对应需求报告：
 * <ul>
 *   <li>G-013  返回强类型 {@link Customer}，不再返回 {@code Object[]}</li>
 *   <li>G-001  新增走纯 INSERT，ID 冲突即失败，<b>不再静默覆盖</b>已有记录</li>
 *   <li>G-012  SQL 异常不再被吞掉，改为抛出已归类的 {@link DataAccessException}</li>
 * </ul>
 *
 * <p>客户是单表写入，不涉及跨表事务（G-010 只影响房屋与房东的组合写入）。
 */
public class CustomerDAO {

    private static final String INSERT_SQL =
            "INSERT INTO customers (id, name, phone, requirements) VALUES (?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE customers SET name = ?, phone = ?, requirements = ? WHERE id = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT id, name, phone, requirements FROM customers ORDER BY id";

    /** 客户 ID 是否已存在 */
    public boolean exists(String customerId) {
        String sql = "SELECT 1 FROM customers WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** 新增客户。ID 已存在时返回 false，不修改原记录 */
    public boolean insertCustomer(Customer customer) {
        return write(customer, false);
    }

    /** 更新客户。记录不存在时返回 false */
    public boolean updateCustomer(Customer customer) {
        return write(customer, true);
    }

    private boolean write(Customer customer, boolean update) {
        String sql = update ? UPDATE_SQL : INSERT_SQL;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (update) {
                stmt.setString(1, customer.getName());
                stmt.setString(2, customer.getPhone());
                stmt.setString(3, customer.getRequirements());
                stmt.setString(4, customer.getId());
            } else {
                stmt.setString(1, customer.getId());
                stmt.setString(2, customer.getName());
                stmt.setString(3, customer.getPhone());
                stmt.setString(4, customer.getRequirements());
            }

            int affected = stmt.executeUpdate();
            System.out.println((update ? "更新" : "新增") + "客户成功: " + customer.getId());
            return affected > 0;

        } catch (SQLException e) {
            System.err.println((update ? "更新" : "新增") + "客户失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
    }

    public List<Customer> getAllCustomers() {
        List<Customer> customers = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                customers.add(new Customer(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("requirements")));
            }
        } catch (SQLException e) {
            System.err.println("查询客户列表失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
        return customers;
    }

    public boolean deleteCustomer(String customerId) {
        String sql = "DELETE FROM customers WHERE id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, customerId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("删除客户失败: " + e.getMessage());
            throw DataAccessException.from(e);
        }
    }
}
