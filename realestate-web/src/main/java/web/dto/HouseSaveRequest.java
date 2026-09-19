package web.dto;

/**
 * 新增 / 编辑房屋的请求体。对应需求报告 R-004 阶段 3。
 *
 * <p>用 {@code record} 而不是拿到 core 的 {@code House} 直接反序列化：
 * 实体字段名是「数据库怎么叫」，请求字段名是「接口对外承诺什么」。分开之后，
 * 给实体加字段不会悄悄改变接口契约；反过来，接口也不必被迫接受调用方不该传的东西。
 *
 * <p>{@code area} 用包装类型 {@code Double} 而不是 {@code double}：缺这个字段时
 * 得到 {@code null}，可以给出「面积必须大于 0」这样一句有用的提示；
 * 若用基本类型，Jackson 会直接抛解析异常，用户看到的是「请求体不是合法的 JSON」，
 * 跟真实原因（漏填面积）毫无关系。
 */
public record HouseSaveRequest(String id,
                               String type,
                               Double area,
                               String address,
                               String status,
                               String landlordId,
                               String landlordName,
                               String landlordContact) {
}
