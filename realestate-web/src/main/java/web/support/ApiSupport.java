package web.support;

import org.springframework.http.HttpStatus;
import util.Result;
import util.Session;
import web.exception.ApiException;

/**
 * 接口层的两个共用动作：把 core 的失败结果翻成 HTTP 异常、读取类操作的权限校验。
 * 对应需求报告 R-004 阶段 4。
 *
 * <p>抽出来是因为房屋 / 客户 / 带看三组接口都需要它们。这类「翻译规则」一旦各写一份，
 * 迟早会出现某个接口把 409 写成 400——而这种偏差很难被发现，因为调用方看到的
 * 文案是一样的，只有状态码不同。
 *
 * <p>放在独立类里而不是做成抽象基类：它是两个无状态动作的组合，
 * 「继承一个 Controller 基类」会顺带把 Spring 的注解继承问题引进来，不划算。
 */
public final class ApiSupport {

    private ApiSupport() {
    }

    /**
     * 把 core 的失败结果翻成对应的 HTTP 异常；成功则什么都不做。
     *
     * <pre>
     *   Result.Kind.VALIDATION / OTHER  →  400  参数或业务规则不通过
     *   Result.Kind.CONFLICT            →  409  ID 已被占用
     *   Result.Kind.NOT_FOUND           →  404  记录不存在（可能已被他人删除）
     *   Result.Kind.PERMISSION          →  403  已登录但权限不足
     * </pre>
     */
    public static void ensureSuccess(Result result) {
        if (result.isSuccess()) {
            return;
        }
        HttpStatus status = switch (result.getKind()) {
            case CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PERMISSION -> HttpStatus.FORBIDDEN;
            // VALIDATION 与 OTHER 都是「请求本身办不到」，归 400
            case VALIDATION, OTHER -> HttpStatus.BAD_REQUEST;
        };
        throw new ApiException(status, result.getMessage());
    }

    /**
     * 读取类操作的权限校验。
     *
     * <p>「界面藏起按钮不算安全措施」的另一半：即便调用方绕过界面直接打接口，
     * 也得先过这一关。ADMIN 与 AGENT 都持有各模块的 view 权限，所以正常使用无感；
     * 真正被挡住的是「角色未知」这类异常情况——未知角色在 {@code Permissions.of} 里
     * 返回空权限集，一律拒绝（fail-safe，与 R-001 的约定一致）。
     *
     * @param permission 需要的权限点，取自 core 的 {@code Permissions}
     * @param subject    提示文案里的对象名，如「房屋」「客户」
     */
    public static void requirePermission(String permission, String subject) {
        if (!Session.can(permission)) {
            throw ApiException.forbidden("权限不足：当前账号没有查看" + subject + "数据的权限。");
        }
    }
}
