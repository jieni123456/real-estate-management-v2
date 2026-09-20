package web.controller;

import controller.StatsController;
import model.Overview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import util.Permissions;
import web.dto.ApiResponse;
import web.dto.OverviewVO;
import web.support.ApiSupport;

import java.util.List;

/**
 * 概览统计接口。对应需求报告 R-004 阶段 5（也是 G-009 的 Web 端形态）。
 *
 * <p>只读聚合，没有写入与删除，因此只有「读取类权限校验」这一道关卡。
 *
 * <p>读取失败时 core 会抛 {@code DataAccessException}，本方法<b>不</b>捕获 ——
 * 由全局异常处理器转成 503。这很关键：概览页最容易出的错就是数据库连不上，
 * 而那时如果把失败当成「0」，用户看到的是「系统里一套房都没有」，
 * 与真相（根本没读到）正好相反。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsApiController {

    private final StatsController statsController = new StatsController();

    /**
     * 概览数据：五项指标 + 户型分布。
     *
     * <p>权限沿用 {@code house:view}，与桌面端一致 —— 桌面端的概览页导航项就是用
     * 它控制的（ADMIN 与 AGENT 都持有）。若将来要让统计只对管理员可见，
     * 应新增一个权限点并同步更新 R-001 的权限矩阵，而不是在这里临时写死角色名。
     */
    @GetMapping("/overview")
    public ApiResponse<OverviewVO> overview() {
        ApiSupport.requirePermission(Permissions.HOUSE_VIEW, "统计");

        Overview data = statsController.loadOverview();

        List<OverviewVO.TypeCountVO> typeCounts = data.getTypeCounts().stream()
                .map(item -> new OverviewVO.TypeCountVO(item.getType(), item.getCount()))
                .toList();

        return ApiResponse.ok(new OverviewVO(
                data.getHouseCount(),
                data.getVacantCount(),
                data.getCustomerCount(),
                data.getLandlordCount(),
                data.getViewingCount(),
                typeCounts));
    }
}
