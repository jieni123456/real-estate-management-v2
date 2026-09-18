package web.security;

import model.User;
import org.springframework.stereotype.Component;
import util.UserHolder;

/**
 * Web 端的「当前用户」实现：每个线程一份。对应需求报告 R-004 / G-022。
 *
 * <p>为什么这样就够了：Tomcat 处理一个请求占用一个线程，因此「每线程一份」
 * 恰好等于「每请求一份」——并发的 A、B 各拿各的，不会像桌面端的静态字段那样
 * 互相顶掉身份。
 *
 * <p><b>请求结束必须清理。</b>Tomcat 的线程是复用的，不清理就会把上一个请求的
 * 用户留给下一个请求——那比「被覆盖」更危险，因为它等于让下一个请求冒用别人的身份
 * 通过权限检查。清理动作放在拦截器的 afterCompletion 里，凡是拦截器匹配到的路径
 * 都会执行到，包括登录接口本身（理由见 {@link AuthInterceptor}）。
 */
@Component
public class ThreadLocalUserHolder implements UserHolder {

    private final ThreadLocal<User> current = new ThreadLocal<>();

    @Override
    public User get() {
        return current.get();
    }

    @Override
    public void set(User user) {
        current.set(user);
    }

    @Override
    public void clear() {
        // 用 remove 而不是 set(null)：remove 会一并清掉 ThreadLocalMap 里的条目，
        // 线程池场景下才不会让这些条目长期挂在复用的线程上。
        current.remove();
    }
}
