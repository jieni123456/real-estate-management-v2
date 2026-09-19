package web.dto;

import model.Viewing;
import util.Formats;

/**
 * 带看记录的对外表示。对应需求报告 R-004 阶段 4。
 *
 * <p><b>为什么时间给两个字段：</b>
 * <ul>
 *   <li>{@code viewedAt}——含秒（{@code 2026-09-19 10:18:00}），供表单回填与提交。
 *       它必须能无损往返，所以带秒；</li>
 *   <li>{@code viewedAtText}——不过秒（{@code 2026-09-19 10:18}），供表格展示。
 *       表格里那一列挤着秒没有意义。</li>
 * </ul>
 *
 * <p>两个格式都出自 core 的 {@link Formats}，导入 CSV 时用的是同一份格式化方法——
 * 这样「屏幕上的时间」与「文件里的时间」不会出现两种写法。
 *
 * <p>客户姓名与房屋地址是查询时 JOIN 出来的展示字段，不是记录本身的属性。
 */
public record ViewingVO(long id,
                        String customerId,
                        String customerName,
                        String houseId,
                        String houseAddress,
                        String viewedAt,
                        String viewedAtText,
                        String result,
                        String note) {

    public static ViewingVO from(Viewing viewing) {
        return new ViewingVO(
                viewing.getId(),
                viewing.getCustomerId(),
                viewing.getCustomerName(),
                viewing.getHouseId(),
                viewing.getHouseAddress(),
                Formats.dateTimeWithSeconds(viewing.getViewedAt()),
                Formats.dateTime(viewing.getViewedAt()),
                viewing.getResult(),
                viewing.getNote());
    }
}
