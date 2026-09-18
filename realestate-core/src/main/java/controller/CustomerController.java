package controller;

import model.Customer;
import service.CustomerService;
import service.LogService;
import service.ViewingService;
import util.DataAccessException;
import util.Permissions;
import util.Result;
import util.Session;
import util.Validators;

import java.util.List;

/**
 * 客户相关的业务入口。
 *
 * <p>对应需求报告：
 * <ul>
 *   <li>G-001  新增走纯 INSERT，ID 冲突明确报错，绝不覆盖原记录</li>
 *   <li>G-002  提供编辑入口（客户 ID 为主键，不可修改）</li>
 *   <li>G-012  数据库异常转成用户能看懂的说明</li>
 *   <li>G-017  新增 / 编辑 / 删除 / 导出写操作日志</li>
 * </ul>
 */
public class CustomerController {

    private final CustomerService customerService = new CustomerService();
    private final LogService logService = new LogService();
    private final ViewingService viewingService = new ViewingService();

    // ---------------------------------------------------------------- 新增

    /**
     * 新增客户。
     *
     * <p>对应需求报告 G-001：ID 已存在时<b>明确报错并拒绝</b>，绝不覆盖原记录。
     */
    public Result addCustomer(String id, String name, String phone, String requirements) {
        Customer customer = buildCustomer(id, name, phone, requirements);

        String invalid = validate(customer);
        if (invalid != null) {
            return Result.fail(invalid);
        }

        try {
            if (customerService.existsCustomer(customer.getId())) {
                return Result.fail("客户ID「" + customer.getId() + "」已存在。请换一个ID，"
                        + "或选中该客户后用「编辑客户」修改它。");
            }

            if (!customerService.insertCustomer(customer)) {
                return Result.fail("保存失败：记录未写入。");
            }

            logService.record("新增客户", customer.getId(), customer.getName());
            return Result.ok("客户添加成功");

        } catch (DataAccessException e) {
            return Result.fail(describe(e, "客户"));
        }
    }

    // ---------------------------------------------------------------- 编辑

    /** 更新客户。对应需求报告 G-002。客户ID 不可修改 */
    public Result updateCustomer(String id, String name, String phone, String requirements) {
        Customer customer = buildCustomer(id, name, phone, requirements);

        String invalid = validate(customer);
        if (invalid != null) {
            return Result.fail(invalid);
        }

        try {
            if (!customerService.existsCustomer(customer.getId())) {
                return Result.fail("客户「" + customer.getId() + "」已不存在，可能已被其他人删除。");
            }

            if (!customerService.updateCustomer(customer)) {
                return Result.fail("保存失败：记录未更新。");
            }

            logService.record("编辑客户", customer.getId(), customer.getName());
            return Result.ok("客户已更新");

        } catch (DataAccessException e) {
            return Result.fail(describe(e, "客户"));
        }
    }

    // ---------------------------------------------------------------- 删除

    /**
     * 删除客户。需 ADMIN 权限（R-001：AGENT 可增可查但不能删）。
     *
     * <p>界面层已把无权用户的删除按钮置灰，这里是第二道防线——防止绕过界面直接调用。
     */
    public Result deleteCustomer(String customerId) {
        if (!Session.can(Permissions.CUSTOMER_DELETE)) {
            System.err.println("[权限不足] " + Session.currentUserLabel() + " 尝试删除客户，已拦截");
            return Result.fail("权限不足：当前账号（" + Session.currentRoleName()
                    + "）没有删除客户的权限。");
        }

        try {
            if (!customerService.deleteCustomer(customerId)) {
                return Result.fail("删除失败：该客户已不存在。");
            }
            logService.record("删除客户", customerId, "");
            return Result.ok("客户删除成功");

        } catch (DataAccessException e) {
            return Result.fail(describe(e, "客户"));
        }
    }

    // ---------------------------------------------------------------- 查询

    /**
     * 全部客户。读取失败时不在控制器里吞掉异常——「空列表」与「数据库连不上」
     * 必须区分开，由界面层捕获 {@link DataAccessException} 并提示。
     */
    public List<Customer> getAllCustomers() {
        System.out.println("获取所有客户信息");
        return customerService.getAllCustomers();
    }

    /** 供界面层判断是否启用「删除客户」按钮 */
    public boolean canDelete() {
        return Session.can(Permissions.CUSTOMER_DELETE);
    }

    /** 记录一次导出（G-014 / G-017） */
    public void recordExport(int count, String fileName) {
        logService.record("导出客户", fileName, "共 " + count + " 条");
    }

    /**
     * 该客户关联的带看记录条数（G-008）。
     * 删除客户时外键会级联删掉这些记录，界面需要先告诉用户会连带删掉多少。
     */
    public int countViewings(String customerId) {
        return viewingService.countByCustomer(customerId);
    }

    // ---------------------------------------------------------------- 内部

    private Customer buildCustomer(String id, String name, String phone, String requirements) {
        return new Customer(trim(id), trim(name), trim(phone), trim(requirements));
    }

    /** 校验通过返回 null，否则返回给用户看的原因 */
    private String validate(Customer customer) {
        String error = Validators.requiredText("客户ID", customer.getId(), 50);
        if (error != null) {
            return error;
        }
        error = Validators.requiredText("姓名", customer.getName(), 100);
        if (error != null) {
            return error;
        }
        error = Validators.phone("电话", customer.getPhone());
        if (error != null) {
            return error;
        }
        return Validators.optionalText("需求描述", customer.getRequirements(), 500);
    }

    /** 把数据访问异常转成给用户的一句话（G-012） */
    private String describe(DataAccessException e, String subject) {
        if (e.getKind() == DataAccessException.Kind.DUPLICATE_KEY) {
            return "该" + subject + "ID 已存在，请换一个 ID。";
        }
        return e.userMessage();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
