package service;

import dao.StatsDAO;
import model.Overview;

public class StatsService {

    private final StatsDAO statsDAO = new StatsDAO();

    public Overview loadOverview() {
        System.out.println("从DAO获取统计数据");
        return statsDAO.loadOverview();
    }
}
