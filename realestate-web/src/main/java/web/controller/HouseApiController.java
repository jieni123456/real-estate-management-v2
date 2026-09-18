package web.controller;

import controller.HouseController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.dto.ApiResponse;
import web.dto.HouseVO;

import java.util.List;

/**
 * 房屋接口。对应需求报告 R-004 / G-024。
 *
 * <p>本阶段只做「列表」这一条——它的目的是把整条链路跑通：请求带 token → 拦截器解出
 * 用户 → 调 core 的 {@link HouseController} → 转成 DTO → 统一外壳返回。
 * 增 / 改 / 删 / 导出放在阶段 3，届时照这个模式扩展即可。
 */
@RestController
@RequestMapping("/api/houses")
public class HouseApiController {

    private final HouseController houseController = new HouseController();

    /**
     * 房屋列表。
     *
     * <p>读取失败时 core 会抛 {@code DataAccessException}，本方法<b>不</b>捕获——
     * 由全局异常处理器转成 503 加一句说明。这一点与桌面端保持一致：
     * 「空列表」与「读不出来」必须区分开，否则调用方会以为数据丢了。
     */
    @GetMapping
    public ApiResponse<List<HouseVO>> list() {
        List<HouseVO> houses = houseController.getAllHouses().stream()
                .map(HouseVO::from)
                .toList();
        return ApiResponse.ok(houses);
    }
}
