package service;

import dao.CustomerDAO;
import model.Customer;

import java.util.List;

public class CustomerService {

    private final CustomerDAO customerDAO = new CustomerDAO();

    public boolean insertCustomer(Customer customer) {
        System.out.println("新增客户: " + customer.getId());
        return customerDAO.insertCustomer(customer);
    }

    public boolean updateCustomer(Customer customer) {
        System.out.println("更新客户: " + customer.getId());
        return customerDAO.updateCustomer(customer);
    }

    public boolean existsCustomer(String customerId) {
        return customerDAO.exists(customerId);
    }

    public List<Customer> getAllCustomers() {
        System.out.println("从DAO获取所有客户");
        return customerDAO.getAllCustomers();
    }

    public boolean deleteCustomer(String customerId) {
        System.out.println("删除客户: " + customerId);
        return customerDAO.deleteCustomer(customerId);
    }
}
