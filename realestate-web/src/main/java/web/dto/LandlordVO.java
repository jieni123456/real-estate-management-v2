package web.dto;

import model.Landlord;

/**
 * 房东的对外表示，供「新增 / 编辑房屋」对话框的下拉选择使用。对应需求报告 G-007。
 *
 * <p>下拉需要的是「ID · 姓名」这种便于辨认的文本，以及选中后回填电话——
 * 所以这里把三者都返回，而不是只给一个 ID 让前端再查一次。
 */
public record LandlordVO(String id, String name, String contact) {

    public static LandlordVO from(Landlord landlord) {
        return new LandlordVO(
                landlord.getId(),
                landlord.getName(),
                landlord.getContact());
    }
}
