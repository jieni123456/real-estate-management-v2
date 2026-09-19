package web.controller;

import controller.CustomerController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import util.CsvExporter;
import util.CustomerQuery;
import util.Permissions;
import util.Result;
import web.dto.ApiResponse;
import web.dto.CustomerDeletionInfoVO;
import web.dto.CustomerSaveRequest;
import web.dto.CustomerVO;
import web.exception.ApiException;
import web.support.ApiSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户接口。对应需求报告 R-004 阶段 4。
 *
 * <p>结构与 {@link HouseApiController} 完全同构——这是有意的：阶段 3 把房屋那条链路
 * 走通并验证过，客户与带看照它复制，风险最低。三个模块共用
 * {@link ApiSupport} 里的失败翻译与权限校验，避免各写一份导致状态码漂移。
 *
 * <p>本类只做「HTTP 语义」这一层：取参、判空、把 core 的结果翻成响应体与状态码。
 * 校验、权限、日志留痕全在 core 的 {@link CustomerController} 里，桌面端调的是
 * 同一批方法，因此两个界面不会出现两套规则。
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerApiController {

    /** 导出文件的表头。顺序与界面表格、桌面端导出一致 */
    private static final String[] CSV_HEADER = {"ID", "姓名", "电话", "需求描述"};

    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final CustomerController customerController = new CustomerController();

    // ---------------------------------------------------------------- 查询

    /**
     * 客户列表。
     *
     * <p>读取失败时 core 会抛 {@code DataAccessException}，本方法<b>不</b>捕获——
     * 由全局异常处理器转成 503 加一句说明。「空列表」与「读不出来」必须区分开。
     */
    @GetMapping
    public ApiResponse<List<CustomerVO>> list() {
        ApiSupport.requirePermission(Permissions.CUSTOMER_VIEW, "客户");
        return ApiResponse.ok(readAll());
    }

    /**
     * 删除前的后果预告（G-008）。
     *
     * <p>删客户会连带删掉他的全部带看记录（外键 ON DELETE CASCADE），
     * 所以确认框弹出前先问一次这里，把条数摆给用户看。
     */
    @GetMapping("/{id}/deletion-info")
    public ApiResponse<CustomerDeletionInfoVO> deletionInfo(@PathVariable String id) {
        ApiSupport.requirePermission(Permissions.CUSTOMER_VIEW, "客户");

        boolean exists = readAll().stream().anyMatch(customer -> customer.id().equals(id));
        if (!exists) {
            throw ApiException.notFound("客户「" + id + "」不存在，可能已被删除");
        }

        return ApiResponse.ok(new CustomerDeletionInfoVO(id, customerController.countViewings(id)));
    }

    /**
     * 导出 CSV。对应需求报告 G-014。
     *
     * <p>带 keyword 参数的理由与房屋导出一致：导出的必须是「屏幕上看到的那几行」。
     * 做法是服务端按同样的条件用 {@link CustomerQuery} 重算一遍，
     * 而不是让前端把行数据传回来——这样筛选规则只有一份实现。
     *
     * <p>文件以 UTF-8 + BOM 写出：少了 BOM，Excel 双击打开会把中文显示成乱码。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword) {
        ApiSupport.requirePermission(Permissions.CUSTOMER_VIEW, "客户");

        List<CustomerVO> rows = CustomerQuery.filter(customerController.getAllCustomers(), keyword)
                .stream()
                .map(CustomerVO::from)
                .toList();

        String fileName = "客户列表_" + CsvExporter.today() + ".csv";
        byte[] body = CsvExporter.buildWithBom(toRows(rows)).getBytes(StandardCharsets.UTF_8);

        // 导出也要留痕（G-017）
        customerController.recordExport(rows.size(), fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(CSV_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .body(body);
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 新增客户。
     *
     * <p>ID 已存在时返回 <b>409</b>，而不是静默覆盖原记录——G-001 那条修复在
     * HTTP 层面的表达。新增与编辑分成两个接口，正是为了让「冲突」成为一个明确的
     * 错误，而不是一个「看起来成功了」的结果。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> create(@RequestBody CustomerSaveRequest request) {
        Result result = customerController.addCustomer(
                request.id(), request.name(), request.phone(), request.requirements());

        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    /**
     * 编辑客户。
     *
     * <p>客户ID 不可修改，以路径上的为准。请求体里若也带了 id 且与路径不一致，
     * 直接判为客户端错误——与其猜「以哪个为准」，不如让这种矛盾当场暴露。
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id,
                                    @RequestBody CustomerSaveRequest request) {
        if (isNotBlank(request.id()) && !id.trim().equals(request.id().trim())) {
            throw ApiException.badRequest(
                    "路径中的客户ID「" + id + "」与请求体中的「" + request.id() + "」不一致");
        }

        Result result = customerController.updateCustomer(
                id, request.name(), request.phone(), request.requirements());

        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    /**
     * 删除客户。需 ADMIN 权限（R-001：AGENT 可增可查但不能删）。
     *
     * <p>权限判断在 core 的 {@code deleteCustomer} 里，返回 {@code Kind.PERMISSION}，
     * 这里翻成 403。界面上的置灰只是体验，这里才是真正拦住的地方。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        Result result = customerController.deleteCustomer(id);
        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    // ---------------------------------------------------------------- 内部

    private List<CustomerVO> readAll() {
        return customerController.getAllCustomers().stream()
                .map(CustomerVO::from)
                .toList();
    }

    private List<String[]> toRows(List<CustomerVO> customers) {
        List<String[]> rows = new ArrayList<>();
        rows.add(CSV_HEADER.clone());
        for (CustomerVO customer : customers) {
            rows.add(new String[]{
                    customer.id(),
                    customer.name(),
                    customer.phone(),
                    customer.requirements()});
        }
        return rows;
    }

    /**
     * 下载响应头。
     *
     * <p>同时给两个文件名：{@code filename} 是 ASCII 兜底（老客户端只认这个），
     * {@code filename*=UTF-8''…} 是 RFC 5987 的写法，现代浏览器据此显示中文名。
     */
    private String contentDisposition(String fileName) {
        String ascii = "customers_" + CsvExporter.today() + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
