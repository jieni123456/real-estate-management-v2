package util;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 数据访问异常。对应需求报告 G-012。
 *
 * <p>改造前：DAO 把 {@code SQLException} 吞掉，只返回 false 或 null。上层于是只能
 * 得到「成没成」这一个信息，分不清是数据库连不上、主键冲突、还是外键约束不满足，
 * 界面只能一律提示「添加失败」——用户不知道该改哪里，排查也无从下手。
 *
 * <p>改造后：DAO 把驱动异常归类为 {@link Kind} 向上抛，Controller 捕获后转成
 * 用户能看懂的说明（{@link #userMessage()}）。界面上不再出现驱动的原始报错——
 * 那些信息既长又包含内部细节。
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 失败原因的分类 */
    public enum Kind {
        /** 连不上数据库：MySQL 未启动、地址或账号口令不对 */
        CONNECTION,
        /** 主键或唯一键冲突（MySQL 1062） */
        DUPLICATE_KEY,
        /** 完整性约束不满足，例如外键引用了不存在的记录（MySQL 1451 / 1452） */
        CONSTRAINT,
        /** 其它数据库错误 */
        UNKNOWN
    }

    /** MySQL 重复键 */
    private static final int ER_DUP_ENTRY = 1062;
    /** MySQL 外键：子行约束（引用了不存在的父记录） */
    private static final int ER_NO_REFERENCED_ROW = 1452;
    /** MySQL 外键：父行仍被引用 */
    private static final int ER_ROW_IS_REFERENCED = 1451;

    private final Kind kind;

    public DataAccessException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * 把驱动抛出的 SQLException 归类。
     *
     * <p>判断顺序有讲究：先看错误码（最精确），再看 SQLState，最后看异常类名。
     * 刻意不 import 具体的驱动类，保持 util 层与数据库驱动解耦。
     */
    public static DataAccessException from(SQLException e) {
        int code = e.getErrorCode();
        String state = e.getSQLState();

        if (code == ER_DUP_ENTRY) {
            return new DataAccessException(Kind.DUPLICATE_KEY, "主键或唯一键冲突", e);
        }
        if (code == ER_NO_REFERENCED_ROW || code == ER_ROW_IS_REFERENCED) {
            return new DataAccessException(Kind.CONSTRAINT, "外键约束不满足", e);
        }
        if (e instanceof SQLIntegrityConstraintViolationException) {
            return new DataAccessException(Kind.CONSTRAINT, "完整性约束不满足", e);
        }
        // SQLState 08 开头即「连接异常」；连不上时驱动抛的是 CommunicationsException
        if (state != null && state.startsWith("08")) {
            return new DataAccessException(Kind.CONNECTION, "无法连接数据库", e);
        }
        if (e.getClass().getSimpleName().contains("Communications")) {
            return new DataAccessException(Kind.CONNECTION, "无法连接数据库", e);
        }
        return new DataAccessException(Kind.UNKNOWN, e.getMessage(), e);
    }

    /** 给用户看的说明。界面层应当展示这个，而不是 {@code getMessage()} */
    public String userMessage() {
        switch (kind) {
            case CONNECTION:
                return "无法连接数据库。请确认 MySQL 已启动，且 db.properties 中的地址、"
                        + "用户名与密码正确。";
            case DUPLICATE_KEY:
                return "该记录已存在（主键或唯一键冲突）。";
            case CONSTRAINT:
                return "数据关联不正确，例如引用了不存在的房东或该记录仍被其它数据引用。";
            default:
                return "数据库操作失败：" + (getMessage() == null ? "未知错误" : getMessage());
        }
    }
}
