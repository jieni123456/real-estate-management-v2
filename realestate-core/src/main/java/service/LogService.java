package service;

import dao.LogDAO;
import model.OperationLog;
import util.DataAccessException;
import util.Permissions;
import util.Session;

import java.util.List;

/**
 * 操作日志。对应需求报告 G-017。
 *
 * <p><b>写与读遵循两条不同的规矩，别把它们混起来：</b>
 *
 * <ul>
 *   <li><b>写</b>：绝不向上抛异常。日志是辅助功能，不能因为「日志表出问题」
 *       把「用户无法添加房屋」放大出来。写失败只记到控制台。</li>
 *   <li><b>读</b>：失败照常抛 {@link DataAccessException}，由调用方提示。
 *       这与 G-012 定下的规矩一致 ——「没有任何操作记录」和「记录读不出来」
 *       是两件事，混成一个空列表会让人误以为系统没人用过。</li>
 * </ul>
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

    /**
     * 最近若干条操作。
     *
     * <p>读取失败会抛出 {@link DataAccessException}：调用方需要把「没有记录」
     * 与「读不出来」分开展示（见类注释）。
     */
    public List<OperationLog> getRecent(int limit) {
        return logDAO.findRecent(limit);
    }

    /** 日志里存角色的中文名，便于直接阅读；未登录时留空 */
    private String displayRole(String role) {
        return role == null || role.isEmpty() ? "" : Permissions.displayName(role);
    }
}
