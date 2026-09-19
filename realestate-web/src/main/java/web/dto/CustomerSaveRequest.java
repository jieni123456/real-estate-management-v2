package web.dto;

/**
 * 新增 / 编辑客户的请求体。对应需求报告 R-004 阶段 4。
 *
 * <p>四个字段都用包装类型 {@code String}（本来就都是文本），不做任何本地校验：
 * 长度、电话格式、ID 是否已被占用，全部由 core 的 {@code Validators} 与
 * {@code CustomerController} 判定，失败原因原样回显给用户。
 * <b>校验规则只有一份实现</b>——界面放过、后端拒绝这种困惑不该出现。
 *
 * @param id           客户ID。编辑时忽略（以路径上的为准），新增时必填
 * @param name         姓名
 * @param phone        电话
 * @param requirements 需求描述，可空
 */
public record CustomerSaveRequest(String id,
                                  String name,
                                  String phone,
                                  String requirements) {
}
