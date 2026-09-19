package web.dto;

/**
 * 删除房屋前的「后果预告」。对应需求报告 G-008 与 G-018。
 *
 * <p>删除一套房屋会连带发生两件事，而两件都<u>不能是隐形的</u>：
 * <ol>
 *   <li>它的带看记录会被外键级联删除（{@code viewings.house_id} 是 ON DELETE CASCADE）</li>
 *   <li>若房东名下再无其它房屋，房东记录会被一并清理（G-018，避免留下孤儿房东）</li>
 * </ol>
 *
 * <p>所以删除确认框要先把这个接口的结果摆出来，让用户在点「确认删除」之前
 * 知道自己正在删掉多少东西。
 *
 * @param houseId              目标房屋
 * @param viewingCount         会被级联删除的带看记录条数
 * @param landlordId           该房屋的房东
 * @param landlordName         房东姓名，用于提示文案
 * @param landlordWillBeRemoved 房东名下是否只有这一套房（为真即「删完房东也没了」）
 * @param landlordHouseCount   房东名下房屋总数，供界面展示判断依据
 */
public record DeletionInfoVO(String houseId,
                             int viewingCount,
                             String landlordId,
                             String landlordName,
                             boolean landlordWillBeRemoved,
                             int landlordHouseCount) {
}
