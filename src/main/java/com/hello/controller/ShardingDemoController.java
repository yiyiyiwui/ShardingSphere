package com.hello.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hello.mapper.OrderMapper;
import com.hello.pojo.Order;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 分库分表功能演示 Controller
 *
 * 演示内容：
 *  1. 按 order_id 取模分表（单库多表）
 *  2. 按 buyer_id 取模分库分表（多库多表）
 *  3. 按范围分片（Range Strategy）
 *  4. 跨分片查询（全路由）
 *  5. 分片键等值查询（精准路由）
 *  6. IN 查询（多分片路由）
 *
 * 注意：实际生效的分片策略取决于 application.yml 中 import 的配置文件
 */
@RestController
@RequestMapping("/sharding")
@CrossOrigin(origins = "*")
@Slf4j
public class ShardingDemoController {

    @Resource
    private OrderMapper orderMapper;

    // ==================== 基础 CRUD ====================

    /**
     * 查询所有订单（跨分片全路由查询）
     * ShardingSphere 会把 SQL 广播到所有物理表然后合并结果
     * GET /sharding/orders
     */
    @GetMapping("/orders")
    public Map<String, Object> getAllOrders() {
        long start = System.currentTimeMillis();
        List<Order> orders = orderMapper.selectList(null);
        long cost = System.currentTimeMillis() - start;

        log.info("【全路由查询】查到 {} 条记录，耗时 {}ms", orders.size(), cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "全路由查询：ShardingSphere 广播到所有分片后合并");
        result.put("总条数", orders.size());
        result.put("耗时ms", cost);
        result.put("数据", orders);
        return result;
    }

    /**
     * 按 orderId 精准查询（命中单一分片）
     * ShardingSphere 根据 order_id 计算出目标表，只查那一张表
     * GET /sharding/orders/by-order-id?orderId=10
     */
    @GetMapping("/orders/by-order-id")
    public Map<String, Object> getByOrderId(@RequestParam int orderId) {
        long start = System.currentTimeMillis();

        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId);
        List<Order> orders = orderMapper.selectList(qw);

        long cost = System.currentTimeMillis() - start;

        // 根据分片算法推断目标表（便于理解）
        String targetTable = "tb_order_" + (orderId % 2);
        log.info("【精准路由】orderId={}, 命中表: {}, 查到 {} 条, 耗时 {}ms",
                orderId, targetTable, orders.size(), cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "精准路由：order_id=" + orderId + " → " + targetTable + "（单表查询，性能最优）");
        result.put("分片键", "order_id");
        result.put("分片算法", "order_id % 2 = " + (orderId % 2));
        result.put("命中物理表", targetTable);
        result.put("耗时ms", cost);
        result.put("数据", orders);
        return result;
    }

    /**
     * IN 查询（多分片路由）
     * 例如查 orderId in (1,2,3,4,5)，ShardingSphere 会按分片键拆分到对应表
     * GET /sharding/orders/by-order-ids?orderIds=1,2,3,4,5
     */
    @GetMapping("/orders/by-order-ids")
    public Map<String, Object> getByOrderIds(@RequestParam List<Integer> orderIds) {
        long start = System.currentTimeMillis();

        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.in("order_id", orderIds);
        List<Order> orders = orderMapper.selectList(qw);

        long cost = System.currentTimeMillis() - start;

        // 分析路由情况
        Map<String, List<Integer>> routing = new LinkedHashMap<>();
        routing.put("tb_order_0（偶数orderId）", new ArrayList<>());
        routing.put("tb_order_1（奇数orderId）", new ArrayList<>());
        for (int id : orderIds) {
            routing.get("tb_order_" + (id % 2) + (id % 2 == 0 ? "（偶数orderId）" : "（奇数orderId）")).add(id);
        }

        log.info("【IN路由】orderIds={}, 查到 {} 条, 耗时 {}ms", orderIds, orders.size(), cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "IN查询：ShardingSphere 按分片键拆分请求到对应分表");
        result.put("请求的orderIds", orderIds);
        result.put("路由分析", routing);
        result.put("耗时ms", cost);
        result.put("数据", orders);
        return result;
    }

    /**
     * 按 buyerId 查询（非分片键查询 = 全路由）
     * 因为分片键是 order_id，查 buyer_id 时 ShardingSphere 不知道在哪张表
     * 会广播到所有分表查询（性能较差，但结果正确）
     * GET /sharding/orders/by-buyer?buyerId=15
     */
    @GetMapping("/orders/by-buyer")
    public Map<String, Object> getByBuyerId(@RequestParam int buyerId) {
        long start = System.currentTimeMillis();

        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.eq("buyer_id", buyerId);
        List<Order> orders = orderMapper.selectList(qw);

        long cost = System.currentTimeMillis() - start;
        log.info("【非分片键查询】buyerId={}, 全路由广播, 查到 {} 条, 耗时 {}ms",
                buyerId, orders.size(), cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "⚠️ 非分片键查询：buyer_id 不是分片键，ShardingSphere 广播到所有分表（性能差）");
        result.put("查询字段", "buyer_id（非分片键）");
        result.put("分片行为", "全路由广播（Broadcast）");
        result.put("性能提示", "建议给非分片键查询建二级索引，或考虑冗余存储/ES搜索");
        result.put("耗时ms", cost);
        result.put("数据", orders);
        return result;
    }

    /**
     * 范围查询（跨分片）
     * 按 order_id 范围查询，ShardingSphere 可能路由到多个分片
     * GET /sharding/orders/range?minOrderId=1&maxOrderId=50
     */
    @GetMapping("/orders/range")
    public Map<String, Object> getByRange(
            @RequestParam int minOrderId,
            @RequestParam int maxOrderId) {
        long start = System.currentTimeMillis();

        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.between("order_id", minOrderId, maxOrderId);
        List<Order> orders = orderMapper.selectList(qw);

        long cost = System.currentTimeMillis() - start;
        log.info("【范围查询】orderId between {} and {}, 查到 {} 条, 耗时 {}ms",
                minOrderId, maxOrderId, orders.size(), cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "范围查询：BETWEEN 条件，ShardingSphere 路由到涉及的所有分片");
        result.put("注意", "INLINE取模算法对范围查询无法精准路由，会全表扫描。范围分片建议用 RANGE 算法");
        result.put("耗时ms", cost);
        result.put("数据", orders);
        return result;
    }

    // ==================== 写操作 ====================

    /**
     * 插入单条订单
     * POST /sharding/orders
     * Body: {"orderId":10, "sellerId":100, "buyerId":200}
     */
    @PostMapping("/orders")
    public Map<String, Object> insertOrder(@RequestBody Order order) {
        long start = System.currentTimeMillis();
        orderMapper.insert(order);
        long cost = System.currentTimeMillis() - start;

        String targetTable = "tb_order_" + (order.getOrderId() % 2);
        log.info("【插入】orderId={} → {}, 耗时 {}ms", order.getOrderId(), targetTable, cost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "精准插入：根据 order_id 路由到对应分表");
        result.put("分片键", "order_id = " + order.getOrderId());
        result.put("路由到表", targetTable);
        result.put("耗时ms", cost);
        result.put("插入的数据", order);
        return result;
    }

    /**
     * 按 orderId 更新
     * PUT /sharding/orders
     */
    @PutMapping("/orders")
    public Map<String, Object> updateOrder(@RequestBody Order order) {
        UpdateWrapper<Order> uw = new UpdateWrapper<>();
        uw.eq("order_id", order.getOrderId());
        Order update = new Order();
        update.setSellerId(order.getSellerId());
        update.setBuyerId(order.getBuyerId());
        int rows = orderMapper.update(update, uw);

        String targetTable = "tb_order_" + (order.getOrderId() % 2);
        log.info("【更新】orderId={} → {}, 影响 {} 行", order.getOrderId(), targetTable, rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("路由到表", targetTable);
        result.put("影响行数", rows);
        return result;
    }

    /**
     * 按 orderId 删除
     * DELETE /sharding/orders?orderId=10
     */
    @DeleteMapping("/orders")
    public Map<String, Object> deleteOrder(@RequestParam int orderId) {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId);
        int rows = orderMapper.delete(qw);

        String targetTable = "tb_order_" + (orderId % 2);
        log.info("【删除】orderId={} → {}, 影响 {} 行", orderId, targetTable, rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("路由到表", targetTable);
        result.put("影响行数", rows);
        return result;
    }

    // ==================== 分片策略演示 ====================

    /**
     * 分片路由分析（不执行SQL，纯粹说明路由逻辑）
     * GET /sharding/analyze?orderId=15
     */
    @GetMapping("/analyze")
    public Map<String, Object> analyzeRouting(@RequestParam int orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("输入orderId", orderId);

        // 单库分表路由
        Map<String, String> singleDbSharding = new LinkedHashMap<>();
        singleDbSharding.put("数据库", "shardingsphere（固定单库）");
        singleDbSharding.put("物理表", "tb_order_" + (orderId % 2));
        singleDbSharding.put("算法", "db_master | tb_order_${order_id % 2}");
        result.put("单库分表路由", singleDbSharding);

        // 多库多表路由
        Map<String, String> multiDbSharding = new LinkedHashMap<>();
        multiDbSharding.put("数据库", "db_" + (orderId % 2));
        multiDbSharding.put("物理表", "tb_order_" + (orderId % 3));
        multiDbSharding.put("算法", "db_${order_id % 2} | tb_order_${order_id % 3}");
        result.put("多库多表路由", multiDbSharding);

        result.put("说明", "实际路由取决于 application.yml 中 import 的配置文件");
        return result;
    }
}