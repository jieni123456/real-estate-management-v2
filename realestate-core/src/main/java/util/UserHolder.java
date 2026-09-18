package util;

import model.User;

/**
 * 「当前用户存在哪」的策略。对应需求报告 R-004 / G-022。
 *
 * <p>为什么需要这一层：core 的 controller 里到处是 {@link Session#can(String)}
 * 这类权限判断，而「当前用户」的存放方式在两种界面下完全不同——
 *
 * <ul>
 *   <li><b>桌面端</b>：单窗口，同一时刻只有一个用户，静态字段即可。</li>
 *   <li><b>Web 端</b>：多个请求并发。若继续用静态字段，B 登录的那一刻会把 A 的身份
 *       覆盖掉，此后 A 的所有操作都会以 B 的身份执行——权限控制当场失效。
 *       必须改成「每请求一份」（Tomcat 每个请求占一个线程，故用 ThreadLocal）。</li>
 * </ul>
 *
 * <p>把差异收在这一个接口后面，{@code Session.can(...)} 的全部调用点一行都不用改。
 * 桌面端使用 {@link Session} 内置的默认实现，Web 端在启动时替换为 ThreadLocal 版本。
 */
public interface UserHolder {

    /** 当前用户，未登录时为 null */
    User get();

    /** 设为当前用户 */
    void set(User user);

    /** 清空当前用户 */
    void clear();
}
