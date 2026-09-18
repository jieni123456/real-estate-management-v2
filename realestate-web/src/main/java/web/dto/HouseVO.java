package web.dto;

import model.House;
import model.Landlord;

/**
 * 房屋的对外表示。对应需求报告 G-024。
 *
 * <p>为什么不直接把 core 的 {@code House} 返回给前端：实体是「数据库长什么样」的
 * 映射，接口是「调用方需要什么」的契约。两者一旦合并，往实体上加个字段就会悄悄
 * 改变接口——把转换显式写出来的代价，远小于某天不小心把口令哈希之类的字段带出去。
 *
 * <p>这里刻意把房东信息摊平（而不是嵌一层对象），对表格展示最省事。
 */
public record HouseVO(String id,
                      String type,
                      double area,
                      String address,
                      String status,
                      String landlordId,
                      String landlordName,
                      String landlordContact) {

    public static HouseVO from(House house) {
        Landlord landlord = house.getLandlord();
        return new HouseVO(
                house.getId(),
                house.getType(),
                house.getArea(),
                house.getAddress(),
                house.getStatus(),
                landlord == null ? "" : landlord.getId(),
                landlord == null ? "" : landlord.getName(),
                landlord == null ? "" : landlord.getContact());
    }
}
