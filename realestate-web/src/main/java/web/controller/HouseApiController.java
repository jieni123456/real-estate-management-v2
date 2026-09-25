package web.controller;

import controller.HouseController;
import model.HouseImportReport;
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
import org.springframework.web.multipart.MultipartFile;
import util.CsvExporter;
import util.Formats;
import util.HouseCsv;
import util.HouseQuery;
import util.Permissions;
import util.Result;
import web.dto.ApiResponse;
import web.dto.DeletionInfoVO;
import web.dto.HouseSaveRequest;
import web.dto.HouseVO;
import web.dto.ImportReportVO;
import web.dto.LandlordVO;
import web.exception.ApiException;
import web.support.ApiSupport;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 房屋接口。对应需求报告 R-004 阶段 3。
 *
 * <p>本类只做「HTTP 语义」这一层——取参、判空、把 core 的结果翻译成响应体与状态码。
 * 校验、权限、日志留痕全都在 core 的 {@link HouseController} 里，桌面端调的是同一批
 * 方法，因此两个界面不会出现两套规则。这是双轨并存的前提。
 *
 * <p><b>失败原因 → HTTP 状态码</b>（在 {@link #ensureSuccess} 里统一翻译）：
 *
 * <pre>
 *   Result.Kind.VALIDATION / OTHER  →  400  参数或业务规则不通过
 *   Result.Kind.CONFLICT            →  409  ID 已被占用
 *   Result.Kind.NOT_FOUND           →  404  记录不存在（可能已被他人删除）
 *   Result.Kind.PERMISSION          →  403  已登录但权限不足
 * </pre>
 *
 * <p>所有失败都回 {@code {code, message, data}} 的统一外壳，{@code code} 与状态码一致。
 * 数据库层面的异常（连不上、约束冲突）不在这里捕获，由 {@code GlobalExceptionHandler}
 * 统一归类——「读不出来」与「没数据」必须区分开。
 */
@RestController
@RequestMapping("/api/houses")
public class HouseApiController {

    /**
     * 导出文件的表头。顺序与界面表格、桌面端导出、以及导入时的表头校验共用
     * {@code core} 的 {@link HouseCsv#HEADER} 一份定义 —— 原先网页端与桌面端各写了一份，
     * 支持导入之后，「导出的文件要能原样导回来」就要求这两处逐字一致，
     * 各写各的迟早会漂移。
     */
    private static final String[] CSV_HEADER = HouseCsv.HEADER;

    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final HouseController houseController = new HouseController();

    // ---------------------------------------------------------------- 查询

    /**
     * 房屋列表。
     *
     * <p>读取失败时 core 会抛 {@code DataAccessException}，本方法<b>不</b>捕获——
     * 由全局异常处理器转成 503 加一句说明。这一点与桌面端保持一致：
     * 「空列表」与「读不出来」必须区分开，否则调用方会以为数据丢了。
     */
    @GetMapping
    public ApiResponse<List<HouseVO>> list() {
        ApiSupport.requirePermission(Permissions.HOUSE_VIEW, "房屋");
        return ApiResponse.ok(readAll());
    }

    /** 房东列表，供「新增 / 编辑房屋」对话框的下拉选择使用（G-007） */
    @GetMapping("/landlords")
    public ApiResponse<List<LandlordVO>> landlords() {
        ApiSupport.requirePermission(Permissions.HOUSE_VIEW, "房屋");
        List<LandlordVO> landlords = houseController.getAllLandlords().stream()
                .map(LandlordVO::from)
                .toList();
        return ApiResponse.ok(landlords);
    }

    /**
     * 删除前的后果预告（G-008 / G-018）。
     *
     * <p>删除会连带删掉带看记录，也可能把房东一并清理掉——两件事都不能是隐形的，
     * 所以确认框弹出前先问一次这里。
     */
    @GetMapping("/{id}/deletion-info")
    public ApiResponse<DeletionInfoVO> deletionInfo(@PathVariable String id) {
        ApiSupport.requirePermission(Permissions.HOUSE_VIEW, "房屋");

        HouseVO target = readAll().stream()
                .filter(house -> house.id().equals(id))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("房屋「" + id + "」不存在，可能已被删除"));

        int viewingCount = houseController.countViewings(id);
        int landlordHouseCount = houseController.countHousesByLandlord(target.landlordId());

        return ApiResponse.ok(new DeletionInfoVO(
                id,
                viewingCount,
                target.landlordId(),
                target.landlordName(),
                // 只有这一套房时，删完房东就成孤儿了——G-018 会在同一事务里清掉它
                landlordHouseCount == 1,
                landlordHouseCount));
    }

    /**
     * 导出 CSV。对应需求报告 G-014。
     *
     * <p><b>为什么带 keyword / status 两个参数：</b>导出的必须是「屏幕上看到的那几行」。
     * 做法是服务端按同样的条件重算一遍，而不是让前端把行数据传回来——这样筛选规则
     * 只有 {@link HouseQuery} 一份实现，不会出现「屏幕 3 行、文件 5 行」的偏差。
     *
     * <p>文件以 UTF-8 + BOM 写出：少了 BOM，Excel 双击打开会把中文显示成乱码。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        ApiSupport.requirePermission(Permissions.HOUSE_VIEW, "房屋");

        List<HouseVO> rows = HouseQuery.filter(houseController.getAllHouses(), keyword, status)
                .stream()
                .map(HouseVO::from)
                .toList();

        String fileName = "房屋列表_" + CsvExporter.today() + ".csv";
        byte[] body = CsvExporter.buildWithBom(toRows(rows)).getBytes(StandardCharsets.UTF_8);

        // 导出也要留痕（G-017）
        houseController.recordExport(rows.size(), fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(CSV_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .body(body);
    }

    // ---------------------------------------------------------------- 导入

    /**
     * 批量导入 CSV。对应需求报告 R-006。
     *
     * <p><b>表头不对就整批拒绝（400），不逐行报错。</b>拿客户表、带看表或者随手
     * 整理过的文件来导入时，逐行处理会产出 100 条「字段不合法」——用户会去逐条查
     * 自己的数据，而真正的原因只有一个：导错文件了。所以先用
     * {@link HouseCsv#isExpectedHeader} 判一次，在开始写库之前就挡掉。
     *
     * <p><b>表头对、但某几行有问题</b>时走的才是逐行路径：core 会跳过坏行继续写，
     * 把每一条的行号与原因放进 {@link ImportReportVO} 返回（HTTP 仍是 200 ——
     * 请求本身是被正常处理的，导进去多少条由报告说明，而不是由状态码说明）。
     *
     * <p><b>权限不足返回 403</b>，与删除房屋一致：导入是批量写操作，只给 ADMIN。
     *
     * <p>编码不在这一层操心：{@link HouseCsv#decode} 会处理「Excel 另存为 CSV
     * 存成 GBK」这种情况，这里拿到的已经是正确的文本。
     */
    @PostMapping("/import")
    public ApiResponse<ImportReportVO> importCsv(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("没有收到文件内容，请重新选择文件。");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("文件读取失败：" + e.getMessage());
        }

        List<String[]> rows = HouseCsv.parse(HouseCsv.decode(bytes));
        if (rows.isEmpty()) {
            throw ApiException.badRequest("文件是空的，没有读到任何内容。");
        }
        if (!HouseCsv.isExpectedHeader(rows)) {
            throw ApiException.badRequest(HouseCsv.describeHeaderMismatch(rows));
        }

        // 第 1 行是表头，从第 2 行起才是数据；行号一并带下去，报告里给用户的行号
        // 就能与他手上的文件对上
        HouseImportReport report = houseController.importHouses(
                rows.subList(1, rows.size()), file.getOriginalFilename());

        if (!report.isPermitted()) {
            throw ApiException.forbidden(report.getDenyReason());
        }
        return ApiResponse.ok(report.summary(), ImportReportVO.from(report));
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 新增房屋。
     *
     * <p>ID 已存在时返回 <b>409</b>，而不是静默覆盖原记录——这是 G-001 那条修复在
     * HTTP 层面的表达。新增与编辑分成两个接口，正是为了让「冲突」成为一个明确的错误，
     * 而不是一个「看起来成功了」的结果。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> create(@RequestBody HouseSaveRequest request) {
        Result result = houseController.addHouse(
                request.id(), request.type(), area(request), request.address(),
                request.landlordId(), request.landlordName(), request.landlordContact(),
                request.status());

        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    /**
     * 编辑房屋。
     *
     * <p>房屋ID 不可修改，以路径上的为准。请求体里若也带了 id 且与路径不一致，
     * 直接判为客户端错误——与其猜「以哪个为准」，不如让这种矛盾当场暴露。
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id,
                                    @RequestBody HouseSaveRequest request) {
        if (isNotBlank(request.id()) && !id.trim().equals(request.id().trim())) {
            throw ApiException.badRequest(
                    "路径中的房屋ID「" + id + "」与请求体中的「" + request.id() + "」不一致");
        }

        Result result = houseController.updateHouse(
                id, request.type(), area(request), request.address(),
                request.landlordId(), request.landlordName(), request.landlordContact(),
                request.status());

        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    /**
     * 删除房屋。需 ADMIN 权限（R-001：AGENT 可增可查但不能删）。
     *
     * <p>权限判断在 core 的 {@code deleteHouse} 里，返回 {@code Kind.PERMISSION}，
     * 这里翻成 403。界面上的置灰只是体验，这里才是真正拦住的地方。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        Result result = houseController.deleteHouse(id);
        ApiSupport.ensureSuccess(result);
        return ApiResponse.ok(result.getMessage(), null);
    }

    // ---------------------------------------------------------------- 内部

    private List<HouseVO> readAll() {
        return houseController.getAllHouses().stream()
                .map(HouseVO::from)
                .toList();
    }

    /**
     * 权限校验与「失败结果 → HTTP 状态码」的翻译都移到了
     * {@link ApiSupport}——客户、带看两组接口需要同一套逻辑，各写一份迟早会漂移
     * （某个接口把 409 写成 400，而调用方只看到文案、看不出区别）。
     */

    private List<String[]> toRows(List<HouseVO> houses) {
        List<String[]> rows = new ArrayList<>();
        rows.add(CSV_HEADER.clone());
        for (HouseVO house : houses) {
            rows.add(new String[]{
                    house.id(),
                    house.type(),
                    Formats.area(house.area()),
                    house.address(),
                    house.status(),
                    house.landlordId(),
                    house.landlordName(),
                    house.landlordContact()});
        }
        return rows;
    }

    /**
     * 下载响应头。
     *
     * <p>同时给两个文件名：{@code filename} 是 ASCII 兜底（老客户端只认这个），
     * {@code filename*=UTF-8''…} 是 RFC 5987 的写法，现代浏览器据此显示中文名。
     * 只给中文名的话，部分浏览器会把它丢掉、退化成用 URL 末段当文件名。
     */
    private String contentDisposition(String fileName) {
        String ascii = "houses_" + CsvExporter.today() + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    /** 面积缺省按 0 处理，交给 core 的校验给出「面积必须大于 0」这句有用的提示 */
    private double area(HouseSaveRequest request) {
        return request.area() == null ? 0 : request.area();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
