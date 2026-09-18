package controller;

import model.Overview;
import service.StatsService;

public class StatsController {

    private final StatsService statsService = new StatsService();

    /**
     * 概览页数据。
     *
     * <p>统计是只读聚合，不涉及权限与数据变更，因此不做额外校验。
     * 查询失败时返回 {@link Overview#empty()}，界面显示 0 而不是崩溃。
     */
    public Overview loadOverview() {
        return statsService.loadOverview();
    }
}
