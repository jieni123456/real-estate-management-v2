package web.controller;

import controller.LogController;
import model.OperationLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import util.Permissions;
import web.dto.ApiResponse;
import web.dto.OperationLogVO;
import web.support.ApiSupport;

import java.util.List;

/**
 * 操作日志接口。对应需求报告 G-017 的 Web 端形态（阶段 5 落地）。
 *
 * <p>日志只写不删，接口也只提供查询 —— 日志的价值在于「谁在什么时候动了什么」，
 * 如果能改就不成其为凭据了。
 *
 * <p>权限沿用 {@code house:view}，与桌面端一致（桌面端把「最近操作」放在概览页里，
 * 而概览页由该权限控制）。若日后要让日志只对管理员开放，
 * 应新增独立的权限点并同步更新 R-001 的权限矩阵，而不是在这里写死角色名。
 */
@RestController
@RequestMapping("/api/logs")
public class LogApiController {

    /** 单次最多返回多少条。防止调用方用 limit=999999 把整张表拉出来 */
    private static final int MAX_LIMIT = 200;

    private static final int DEFAULT_LIMIT = 20;

    private final LogController logController = new LogController();

    /**
     * 最近的操作记录，按时间倒序。
     *
     * @param limit 条数，默认 20、上限 200。越界不报错而是夹到边界上 ——
     *              这类参数错误没有提示价值，返回能给的限度即可。
     *
     * <p>读取失败时 core 抛 {@code DataAccessException}，此处不捕获，由全局异常处理器
     * 转 503。「没有任何操作记录」与「日志读不出来」必须区分开 ——
     * 混成因空列表会让人误以为系统没人用过。
     */
    @GetMapping
    public ApiResponse<List<OperationLogVO>> recent(
            @RequestParam(name = "limit", required = false) Integer limit) {

        ApiSupport.requirePermission(Permissions.HOUSE_VIEW, "操作日志");

        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<OperationLogVO> logs = logController.getRecent(size).stream()
                .map(LogApiController::toVO)
                .toList();

        return ApiResponse.ok(logs);
    }

    private static OperationLogVO toVO(OperationLog log) {
        return new OperationLogVO(
                log.getTime(),
                log.getOperator(),
                log.getRole(),
                log.getAction(),
                log.getTarget(),
                log.getDetail());
    }
}
