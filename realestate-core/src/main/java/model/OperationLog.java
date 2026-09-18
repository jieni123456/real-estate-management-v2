package model;

/**
 * 一条操作日志。对应需求报告 G-017。
 *
 * <p>产生于 Controller 层：谁（operator / role）在什么时候（time，由数据库记录）
 * 对什么（target）做了什么（action），必要时附一句说明（detail）。
 */
public class OperationLog {

    private final String time;
    private final String operator;
    private final String role;
    private final String action;
    private final String target;
    private final String detail;

    public OperationLog(String time, String operator, String role,
                        String action, String target, String detail) {
        this.time = time;
        this.operator = operator;
        this.role = role;
        this.action = action;
        this.target = target;
        this.detail = detail;
    }

    public String getTime() {
        return time;
    }

    public String getOperator() {
        return operator;
    }

    public String getRole() {
        return role;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetail() {
        return detail;
    }

    /** 概览页那一行文字，例如「09-16 11:20　admin　删除房屋　101　大王发放」 */
    public String toLine() {
        StringBuilder builder = new StringBuilder();
        builder.append(time == null ? "" : time).append("　");
        builder.append(operator == null ? "?" : operator).append("　");
        builder.append(action == null ? "" : action);
        if (target != null && !target.isEmpty()) {
            builder.append("　").append(target);
        }
        if (detail != null && !detail.isEmpty()) {
            builder.append("　").append(detail);
        }
        return builder.toString();
    }
}
