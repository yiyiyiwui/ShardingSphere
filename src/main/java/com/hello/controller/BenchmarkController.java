package com.hello.controller;

import com.hello.service.BenchmarkService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 性能对比测试接口
 * 用于直观展示分库分表前后的查询/插入性能差异
 */
@RestController
@RequestMapping("/bench")
@CrossOrigin(origins = "*")
@Slf4j
public class BenchmarkController {

    @Resource
    private BenchmarkService benchmarkService;

    /**
     * 批量插入测试（分表模式）
     * POST /bench/insert?count=10000
     * 插入指定条数数据，返回耗时
     */
    @PostMapping("/insert")
    public Map<String, Object> benchInsert(@RequestParam(defaultValue = "10000") int count) {
        log.info("【性能测试】开始批量插入 {} 条数据", count);
        return benchmarkService.benchmarkInsert(count);
    }

    /**
     * 全表查询对比测试
     * GET /bench/query?orderId=50
     * 同时对"普通单表"和"分片路由表"执行查询，对比耗时
     */
    @GetMapping("/query")
    public Map<String, Object> benchQuery(@RequestParam(defaultValue = "50") int orderId) {
        log.info("【性能测试】对比查询 orderId={}", orderId);
        return benchmarkService.benchmarkQuery(orderId);
    }

    /**
     * 全表扫描对比
     * GET /bench/scan
     * 对比分片表 vs 普通表 全量查询耗时
     */
    @GetMapping("/scan")
    public Map<String, Object> benchScan() {
        log.info("【性能测试】全表扫描对比");
        return benchmarkService.benchmarkFullScan();
    }

    /**
     * 清空测试数据
     * DELETE /bench/clear
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clearData() {
        log.info("【性能测试】清空测试数据");
        return benchmarkService.clearAll();
    }

    /**
     * 获取各分表数据分布情况
     * GET /bench/distribution
     * 返回每张物理表的数据量，验证分片均匀性
     */
    @GetMapping("/distribution")
    public Map<String, Object> distribution() {
        return benchmarkService.getDistribution();
    }
}