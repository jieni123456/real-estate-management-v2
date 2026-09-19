package web.controller;

import controller.ViewingController;
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
import util.Formats;
import util.Permissions;
import util.Result;
import util.ViewingQuery;
import util.ViewingRules;
import web.dto.ApiResponse;
import web.dto.CustomerVO;
import web.dto.HouseVO;
import web.dto.ViewingOptionsVO;
import web.dto.ViewingSaveRequest;
import web.dto.ViewingVO;
import web.exception.ApiException;
import web.support.ApiSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 带看记录接口。对应需求报告 R-004 阶段 4。
 *
 * <p>与房屋、客户两个模块同构，共用 {@link ApiSupport} 的失败翻译与权限校验。
 *
 * <p>两处与其它模块不同，都在 core 里解决，这里只是把它们暴露出来：
 * <ul>
 *   <li><b>G-020</b>——只有「空置」的房源能登记带看。下拉数据由 {@code /options}
 *       提供，已按 {@link ViewingRules} 过滤；core 里还有一道兜底拦截，
 *       因为界面过滤只是体验。</li>
 *   <li><b>R-003</b>——结果为「已成交」时，core 会在同一事务里把房屋置为「已租出」。
 *       成功提示里会带上这句说明（{@code message} 字段），免得用户以为系统在背后改数据。</li>
 * </ul>
 *
 * <p>带看时间用 {@code Formats.DATE_TIME_SECONDS_PATTERN} 收发，与前端
 * {@code el-date-picker} 的 value-format 一致，回填与提交对称。
 */
@RestController
@RequestMapping("/api/viewings")
public class ViewingApiController {

    /** 导出文件的表头。顺序与界面表格、桌面端导出一致 */
    private static final String[] CSV_HEADER =
            {"客户ID", "客户姓名", "房屋ID", "地址", "带看时间", "结果", "备注"};

    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final ViewingController viewingController = new ViewingController();

    // ---------------------------------------------------------------- 查询

    @GetMapping
    public ApiResponse<List<ViewingVO>> list() {
        ApiSupport.requirePermission(Permissions.VIEWING_VIEW, "带看");
        return ApiResponse.ok(readAll());
    }

    /**
     * 「登记带看」对话框的两份下拉数据。
     *
     * @param keepHouseId 编辑旧记录时传它原本挂着的房屋 ID。该房源即便已租出也会
     *                    保留在候选里——否则房子后来租出去了，这条记录连改个备注
     *                    都存不下来。新增时不用传
     */
    @GetMapping("/options")
    public ApiResponse<ViewingOptionsVO> options(
            @RequestParam(required = false) String keepHouseId) {

        ApiSupport.requirePermission(Permissions.VIEWING_VIEW, "带看");

        List<CustomerVO> customers = viewingController.getAllCustomers().stream()
                .map(CustomerVO::from)
                .toList();

        List<HouseVO> houses = ViewingRules
                .selectable(viewingController.getAllHouses(), keepHouseId)
                .stream()
                .map(HouseVO::from)
                .toList();

        return ApiResponse.ok(new ViewingOptionsVO(customers, houses));
    }

    /**
     * 导出 CSV。对应需求报告 G-014。
     *
     * <p>keyword 与 result 两个参数缺一不可：导出的必须是「屏幕上看到的那几行」，
     * 由服务端用 {@link ViewingQuery} 按同样的条件重算，而不是让前端传行数据回来。
     *
     * <p>时间列用 {@link ViewingVO#viewedAtText()}（不带秒），与界面表格看到的完全一致。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String result) {
        ApiSupport.requirePermission(Permissions.VIEWING_VIEW, "带看");

        List<ViewingVO> rows = ViewingQuery
                .filter(viewingController.getAllViewings(), keyword, result)
                .stream()
                .map(ViewingVO::from)
                .toList();

        String fileName = "带看记录_" + CsvExporter.today() + ".csv";
        byte[] body = CsvExporter.buildWithBom(toRows(rows)).getBytes(StandardCharsets.UTF_8);

        viewingController.recordExport(rows.size(), fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(CSV_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .body(body);
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 新增带看记录（201）。
     *
     * <p>若结果为「已成交」，core 会把房屋置为「已租出」并把这件事写进 message，
     * 所以前端不能丢掉这个字段。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> create(@RequestBody ViewingSaveRequest request) {
        Result result = viewingController.addViewing(
                request.customerId(), request.houseId(), parseViewedAt(request.viewedAt()),
                request.result(), request.note());

        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id,
                                    @RequestBody ViewingSaveRequest request) {
        Result result = viewingController.updateViewing(
                parseId(id), request.customerId(), request.houseId(),
                parseViewedAt(request.viewedAt()), request.result(), request.note());

        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    /**
     * 删除带看记录。需 ADMIN 权限——与房屋、客户一致，删除是唯一被收回的操作。
     *
     * <p>带看记录没有下游关联，所以不需要「后果预告」那种接口：
     * 删它不会连带删掉任何东西。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        Result result = viewingController.deleteViewing(parseId(id));
        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    // ---------------------------------------------------------------- 内部

    private List<ViewingVO> readAll() {
        return viewingController.getAllViewings().stream()
                .map(ViewingVO::from)
                .toList();
    }

    /**
     * 解析带看时间。
     *
     * <p>刻意把「没填」与「填错」分开说：都归成「格式不正确」会让一个空表单
     * 收到一句看不懂的提示。「不能晚于今天」之类的合理性判断不在这里，
     * 交给 core 的 {@code Validators}。
     */
    private LocalDateTime parseViewedAt(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw ApiException.badRequest("请填写带看时间");
        }
        LocalDateTime parsed = Formats.parseDateTimeWithSeconds(value);
        if (parsed == null) {
            throw ApiException.badRequest(
                    "带看时间格式不正确，应形如 " + Formats.DATE_TIME_SECONDS_PATTERN);
        }
        return parsed;
    }

    /**
     * 解析路径上的记录编号。
     *
     * <p>不直接把 {@code @PathVariable long} 交给 Spring：类型转换失败会抛
     * {@code MethodArgumentTypeMismatchException}，那个异常未必走我们自己的异常处理器，
     * 调用方可能收到一个格式不同的响应体。自己解析就能保证还是统一外壳。
     */
    private long parseId(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("带看记录编号「" + value + "」不是合法的数字");
        }
    }

    private List<String[]> toRows(List<ViewingVO> viewings) {
        List<String[]> rows = new ArrayList<>();
        rows.add(CSV_HEADER.clone());
        for (ViewingVO viewing : viewings) {
            rows.add(new String[]{
                    viewing.customerId(),
                    viewing.customerName(),
                    viewing.houseId(),
                    viewing.houseAddress(),
                    viewing.viewedAtText(),
                    viewing.result(),
                    viewing.note()});
        }
        return rows;
    }

    /**
     * 下载响应头。同时给 ASCII 兜底名与 RFC 5987 的中文名，
     * 只给中文名的话部分浏览器会把它丢掉、退化成用 URL 末段当文件名。
     */
    private String contentDisposition(String fileName) {
        String ascii = "viewings_" + CsvExporter.today() + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
