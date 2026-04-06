package com.hello.mapper;

import com.hello.pojo.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 原生查询 Mapper（不经过 ShardingSphere 路由）
 *
 * 使用场景：
 *  - 直接查指定物理表（如 tb_order_0）而不走分片逻辑
 *  - 跨表 JOIN（ShardingSphere 对复杂 JOIN 支持有限）
 *  - 统计类查询（分表后统计需要手动 union 或用这种方式）
 *
 * 注意：如果不配置 ShardingSphere，这些 SQL 直接打到 db_master
 */
public interface OrderRawMapper {

    /**
     * 直接查 tb_order_0 物理表
     * 场景：已知数据在 _0 表（orderId 为偶数），跳过路由直接查
     */
    @Select("SELECT id, order_id, seller_id, buyer_id FROM tb_order_0")
    List<Order> selectFromTable0();

    /**
     * 直接查 tb_order_1 物理表
     */
    @Select("SELECT id, order_id, seller_id, buyer_id FROM tb_order_1")
    List<Order> selectFromTable1();

    /**
     * 用 UNION ALL 跨物理表查询
     * 场景：ShardingSphere 无法处理的复杂 SQL，手动 UNION
     */
    @Select("SELECT id, order_id, seller_id, buyer_id FROM tb_order_0 " +
            "UNION ALL " +
            "SELECT id, order_id, seller_id, buyer_id FROM tb_order_1 " +
            "ORDER BY order_id")
    List<Order> selectAllUnion();

    /**
     * 各物理表统计（用于数据分布验证）
     */
    @Select("SELECT COUNT(*) FROM tb_order_0")
    long countTable0();

    @Select("SELECT COUNT(*) FROM tb_order_1")
    long countTable1();

    /**
     * 统计某个 orderId 在哪张表
     * 业务代码有时需要明确知道数据路由位置
     */
    @Select("SELECT 'tb_order_0' as tbl, COUNT(*) as cnt FROM tb_order_0 WHERE order_id = #{orderId} " +
            "UNION ALL " +
            "SELECT 'tb_order_1', COUNT(*) FROM tb_order_1 WHERE order_id = #{orderId}")
    List<Map<String, Object>> locateOrder(@Param("orderId") int orderId);
}