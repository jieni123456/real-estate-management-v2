package controller;

import model.OperationLog;
import service.LogService;

import java.util.List;

/**
 * 操作日志的查询入口。写入由各业务 Controller 直接调用 LogService 完成，
 * 不经过本类——否则每个业务动作都要绕一圈，徒增层次。
 */
public class LogController {

    private final LogService logService = new LogService();

    public List<OperationLog> getRecent(int limit) {
        return logService.getRecent(limit);
    }
}
