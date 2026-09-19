package web.dto;

import model.Customer;

/**
 * 客户的对外表示。对应需求报告 R-004 阶段 4（延用 G-024 的做法）。
 *
 * <p>不直接把 core 的 {@code Customer} 返回给前端：实体是「数据库长什么样」的映射，
 * 接口是「调用方需要什么」的契约，两者合并后往实体上加字段就会悄悄改变接口。
 *
 * <p>四个字段全部可返回——客户表里没有需要脱敏的列（房东的加密联系方式才需要）。
 */
public record CustomerVO(String id,
                         String name,
                         String phone,
                         String requirements) {

    public static CustomerVO from(Customer customer) {
        return new CustomerVO(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getRequirements());
    }
}
