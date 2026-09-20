package controller;

import model.Overview;
import service.StatsService;

public class StatsController {

    private final StatsService statsService = new StatsService();

    /**
     * 概览页数据。
     *
     * <p>统计是只读聚合，不涉及权限与数据变更，因此不做额外校验。
     * 读取失败会抛出 {@link util.DataAccessException}（不再吞成空统计）——
     * 桌面端的概览页与 Web 端的接口各自决定怎么提示用户。
     */
    public Overview loadOverview() {
        return statsService.loadOverview();
    }
}
