package service;

import dao.LogDAO;
import model.OperationLog;
import util.DataAccessException;
import util.Permissions;
import util.Session;

import java.util.Collections;
import java.util.List;

/**
 * 操作日志。对应需求报告 G-017。
 *
 * <p><b>核心约定：日志是辅助功能，绝不能因为它失败而让业务操作失败。</b>
 * 因此本类不向上抛 {@link DataAccessException}——写失败只记到控制台，
 * 读失败返回空列表。否则「日志表出问题」会被放大成「用户无法添加房屋」。
 */
public class LogService {

    private final LogDAO logDAO = new LogDAO();

    /**
     * 记录一条当前登录用户的操作。
     *
     * @param action 动作，例如「删除房屋」
     * @param target 操作对象，例如房屋 ID
     * @param detail 补充说明，可为空
     */
    public void record(String action, String target, String detail) {
        recordAs(Session.currentUsername(), Session.currentRole(), action, target, detail);
    }

    /**
     * 以指定身份记录。用于登录失败这类「此刻 Session 里还没有用户」的场景——
     * 此时操作者是尝试登录的那个用户名，角色为空。
     */
    public void recordAs(String operator, String role, String action, String target, String detail) {
        try {
            logDAO.insert(new OperationLog(
                    null, operator, displayRole(role), action, target, detail));
        } catch (DataAccessException e) {
            System.err.println("写操作日志失败（不影响业务操作）: " + e.getMessage());
        }
    }

    /** 最近若干条操作，出错时返回空列表 */
    public List<OperationLog> getRecent(int limit) {
        try {
            return logDAO.findRecent(limit);
        } catch (DataAccessException e) {
            System.err.println("读取操作日志失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 日志里存角色的中文名，便于直接阅读；未登录时留空 */
    private String displayRole(String role) {
        return role == null || role.isEmpty() ? "" : Permissions.displayName(role);
    }
}
