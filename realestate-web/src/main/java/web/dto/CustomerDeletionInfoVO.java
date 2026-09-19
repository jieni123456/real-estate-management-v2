package web.dto;

/**
 * 删除客户前的「后果预告」。对应需求报告 G-008（级联删除）。
 *
 * <p>删一个客户会连带删掉他的全部带看记录（{@code viewings.customer_id} 的外键是
 * ON DELETE CASCADE），而<h2>级联删除不能是隐形的</h2>——确认框必须在用户点下
 * 「确认删除」之前说清楚会连带删掉多少条。
 *
 * <p>比房屋少一档：客户名下没有「附属实体」，所以只有带看记录这一种连带后果。
 *
 * @param customerId    目标客户
 * @param viewingCount  会被级联删除的带看记录条数
 */
public record CustomerDeletionInfoVO(String customerId, int viewingCount) {
}
