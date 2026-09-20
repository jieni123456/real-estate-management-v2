package web.dto;

import java.util.List;

/**
 * 概览页统计（GET /api/stats/overview）。对应需求报告 R-004 阶段 5。
 *
 * <p>照着 core 的 {@code model.Overview} 的形状返回，但**不直接把 core 的模型当返回体**：
 * core 的模型是给两个界面共用的内部结构，接口契约应当自己定一份。
 * 「不把 core 的 model 直接暴露成 JSON」这条从阶段 1 起没变
 * （见 5.12 的 DTO 与实体分离）。
 *
 * @param houseCount    房源总数
 * @param vacantCount   空置（未租出）房源数 —— 这个系统里最接近「库存」的指标
 * @param customerCount 客户总数
 * @param landlordCount 房东总数
 * @param viewingCount  带看记录总数
 * @param typeCounts    各户型的房源数，按数量降序
 */
public record OverviewVO(
        int houseCount,
        int vacantCount,
        int customerCount,
        int landlordCount,
        int viewingCount,
        List<TypeCountVO> typeCounts) {

    /**
     * 单个户型的房源数。
     *
     * @param type  户型名
     * @param count 该户型的房源套数
     */
    public record TypeCountVO(String type, int count) {
    }
}
