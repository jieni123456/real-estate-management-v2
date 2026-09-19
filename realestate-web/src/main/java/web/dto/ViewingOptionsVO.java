package web.dto;

import java.util.List;

/**
 * 「登记带看」对话框需要的两份下拉数据。对应需求报告 R-004 阶段 4 与 G-020。
 *
 * <p>合成一个接口而不是拆成两个请求：这个对话框一打开就同时需要客户与房源，
 * 分两次请求除了多一次往返没有任何好处。
 *
 * @param customers 全部客户。客户能不能再带看没有限制——
 *                  「已租好房的客户不应再出现在下拉里」这条曾被提出，用户复核后
 *                  确认不需要（一个客户可能租多套，也常有替家人朋友看房的情况）
 * @param houses    可登记带看的房源。只含「空置」，过滤规则见
 *                  {@code util.ViewingRules}；编辑一条旧记录时，其原本挂着的房源
 *                  即便已租出也会保留（否则连改备注都存不下来）
 */
public record ViewingOptionsVO(List<CustomerVO> customers, List<HouseVO> houses) {
}
