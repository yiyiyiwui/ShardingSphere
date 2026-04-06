package com.hello.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.hello.mapper.OrderMapper;
import com.hello.pojo.Order;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能基准测试 Service
 *
 * 核心功能：
 *  1. 批量插入（分片表 vs 普通单表）
 *  2. 按分片键查询（精准路由）
 *  3. 全表扫描对比
 *  4. 数据分布统计
 */
@Service
@Slf4j
public class BenchmarkService {

    @Resource
    private OrderMapper orderMapper;  // 走 ShardingSphere 分片路由

    // ==================== 批量插入测试 ====================

    /**
     * 批量插入测试
     * 测试向分片表批量插入 N 条数据的耗时
     *
     * @param count 插入条数
     */
    public Map<String, Object> benchmarkInsert(int count) {
        log.info("【Benchmark】开始插入 {} 条数据到分片表...", count);

        // 先清空分片表，确保对比公平
        orderMapper.delete(null);

        // —— 分片表插入 ——
        long shardStart = System.currentTimeMillis();
        int batchSize = 500; // 每批500条，避免一次性提交太多
        int batches = (count + batchSize - 1) / batchSize;

        AtomicInteger insertedCount = new AtomicInteger(0);

        for (int batch = 0; batch < batches; batch++) {
            int from = batch * batchSize;
            int to = Math.min(from + batchSize, count);
            for (int i = from; i < to; i++) {
                Order o = new Order();
                o.setOrderId(i + 1);
                o.setSellerId((i % 100) + 1);
                o.setBuyerId((i % 200) + 1);
                orderMapper.insert(o);
            }
            int done = insertedCount.addAndGet(to - from);
            log.info("  分片插入进度: {}/{}", done, count);
        }

        long shardCost = System.currentTimeMillis() - shardStart;
        log.info("【Benchmark】分片表插入完成！共 {} 条，耗时 {}ms（{} 秒）",
                count, shardCost, shardCost / 1000.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("插入条数", count);
        result.put("分片表插入耗时ms", shardCost);
        result.put("分片表插入耗时秒", String.format("%.2f", shardCost / 1000.0));
        result.put("平均每条耗时ms", String.format("%.2f", (double) shardCost / count));
        result.put("说明", "分片表插入：ShardingSphere 自动路由到对应物理表，insert 本身无额外开销");
        return result;
    }

    // ==================== 查询对比测试 ====================

    /**
     * 查询性能对比
     * 对同一个 orderId 分别执行：
     *  1. 分片精准查询（只查目标表）
     *  2. 全路由查询（广播到所有分表）
     */
    public Map<String, Object> benchmarkQuery(int orderId) {
        // —— 分片精准查询（带分片键）——
        long shardStart = System.currentTimeMillis();
        QueryWrapper<Order> shardQw = new QueryWrapper<>();
        shardQw.eq("order_id", orderId);
        List<Order> shardResult = orderMapper.selectList(shardQw);
        long shardCost = System.currentTimeMillis() - shardStart;

        // —— 全路由查询（不带分片键，广播查所有表）——
        long broadcastStart = System.currentTimeMillis();
        QueryWrapper<Order> broadcastQw = new QueryWrapper<>();
        broadcastQw.eq("buyer_id", 999999); // 用非分片键查询触发全路由（这个buyer_id一般不存在）
        List<Order> broadcastResult = orderMapper.selectList(broadcastQw);
        long broadcastCost = System.currentTimeMillis() - broadcastStart;

        // —— 全量扫描（不带条件）——
        long scanStart = System.currentTimeMillis();
        long totalCount = orderMapper.selectCount(null);
        long scanCost = System.currentTimeMillis() - scanStart;

        String targetTable = "tb_order_" + (orderId % 2);
        log.info("【查询对比】精准路由={}ms, 全路由={}ms, 总量统计={}ms",
                shardCost, broadcastCost, scanCost);

        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> precise = new LinkedHashMap<>();
        precise.put("查询条件", "order_id = " + orderId + "（分片键）");
        precise.put("命中物理表", targetTable);
        precise.put("耗时ms", shardCost);
        precise.put("查询到条数", shardResult.size());
        precise.put("优点", "只查一张表，性能最优");
        result.put("① 精准路由查询", precise);

        Map<String, Object> broadcast = new LinkedHashMap<>();
        broadcast.put("查询条件", "buyer_id = 999999（非分片键）");
        broadcast.put("路由行为", "广播到所有分表");
        broadcast.put("耗时ms", broadcastCost);
        broadcast.put("查询到条数", broadcastResult.size());
        broadcast.put("缺点", "全表扫描所有分片，数据量大时性能差");
        result.put("② 全路由广播查询", broadcast);

        Map<String, Object> count = new LinkedHashMap<>();
        count.put("操作", "SELECT COUNT(*) 全表统计");
        count.put("耗时ms", scanCost);
        count.put("总条数", totalCount);
        result.put("③ 全量统计", count);

        return result;
    }

    /**
     * 全表扫描对比
     * 统计分片表总量，体现分片后数据聚合查询的开销
     */
    public Map<String, Object> benchmarkFullScan() {
        log.info("【Benchmark】执行全表扫描对比...");

        // 分片表全量查询
        long shardStart = System.currentTimeMillis();
        List<Order> allOrders = orderMapper.selectList(null);
        long shardCost = System.currentTimeMillis() - shardStart;

        // 带条件范围查询（分片精准）
        long rangeStart = System.currentTimeMillis();
        QueryWrapper<Order> rangeQw = new QueryWrapper<>();
        rangeQw.in("order_id", generateRange(1, 50));
        List<Order> rangeOrders = orderMapper.selectList(rangeQw);
        long rangeCost = System.currentTimeMillis() - rangeStart;

        log.info("【全表扫描】全量={}条/{}ms, 范围IN查询={}条/{}ms",
                allOrders.size(), shardCost, rangeOrders.size(), rangeCost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("全量查询（分片合并）", Map.of(
                "总条数", allOrders.size(),
                "耗时ms", shardCost,
                "说明", "ShardingSphere 查所有分表后 merge 结果"
        ));
        result.put("范围IN查询（orderId 1~50）", Map.of(
                "查到条数", rangeOrders.size(),
                "耗时ms", rangeCost,
                "说明", "orderId IN (1..50) 分路由到两个分表"
        ));
        result.put("性能建议", Arrays.asList(
                "尽量带分片键查询，避免全路由广播",
                "大数据量统计考虑异步汇总或定期快照",
                "超大表（>1000万）分页查询必须带分片键，否则内存爆炸"
        ));
        return result;
    }

    // ==================== 数据分布统计 ====================

    /**
     * 统计各物理分表的数据量，验证数据分布均匀性
     * 通过 Hint 强制路由到特定表查询
     */
    public Map<String, Object> getDistribution() {
        log.info("【分布统计】查询各分表数据量...");

        // 通过 order_id 区分奇偶来估算各表数据量
        QueryWrapper<Order> evenQw = new QueryWrapper<>();
        evenQw.apply("order_id % 2 = 0"); // 偶数 orderId → tb_order_0
        long table0Count = orderMapper.selectCount(evenQw);

        QueryWrapper<Order> oddQw = new QueryWrapper<>();
        oddQw.apply("order_id % 2 = 1"); // 奇数 orderId → tb_order_1
        long table1Count = orderMapper.selectCount(oddQw);

        long total = table0Count + table1Count;
        log.info("【分布统计】tb_order_0={}, tb_order_1={}, 合计={}", table0Count, table1Count, total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tb_order_0（偶数orderId路由）", table0Count);
        result.put("tb_order_1（奇数orderId路由）", table1Count);
        result.put("合计", total);

        if (total > 0) {
            result.put("tb_order_0占比", String.format("%.1f%%", table0Count * 100.0 / total));
            result.put("tb_order_1占比", String.format("%.1f%%", table1Count * 100.0 / total));
            result.put("分布均匀度", Math.abs(table0Count - table1Count) < total * 0.05 ? "✅ 均匀" : "⚠️ 不均匀");
        }

        result.put("说明", "取模分片（order_id % 2）理论上奇偶各半，分布非常均匀");
        return result;
    }

    // ==================== 清空数据 ====================

    /**
     * 清空所有分片表数据（慎用！仅用于测试）
     */
    public Map<String, Object> clearAll() {
        log.warn("【清空数据】即将清空所有分片表数据！");
        int deleted = orderMapper.delete(null);
        log.info("【清空数据】删除了 {} 条记录", deleted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("删除条数", deleted);
        result.put("状态", "已清空");
        return result;
    }

    // ==================== 工具方法 ====================

    /** 生成 from~to 的 List<Integer> */
    private List<Integer> generateRange(int from, int to) {
        List<Integer> list = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            list.add(i);
        }
        return list;
    }
}