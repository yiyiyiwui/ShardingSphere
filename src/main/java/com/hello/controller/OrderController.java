package com.hello.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hello.mapper.OrderMapper;
import com.hello.pojo.Order;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分表CRUD测试
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Resource
    private OrderMapper orderMapper;

    /**
     * 查询所有
     * 会把所有分表的数据都返回
     */
    @GetMapping("/getOrder")
    public List<Order> getOrder() {
        return orderMapper.selectList(null);
    }

    /**
     * 插入
     * 会根据的orderId保存到不同的表中
     * 测试数据：
     * {
     * "orderId":"6",
     * "sellerId":"66",
     * "buyerId":"666"
     * }
     */
    @GetMapping("/saveOrder")
    public int saveOrder() {
        try {
            log.info("插入数据测试");
            for (int i = 0; i < 100; i++) {
            Order o = new Order();
                o.setOrderId(i + 1);
                o.setSellerId(i + 5);
                o.setBuyerId(i + 10);
             orderMapper.insert(o);
            }
            return 1;
        } catch (Exception e) {
            log.info("插入数据异常：{}",e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据orderId更新其他数据
     * 测试数据：
     * {
     * "orderId":"6",
     * "sellerId":"66",
     * "buyerId":"666"
     * }
     */
    @PutMapping("/updateOrder")
    public int updateOrder(@RequestBody Order order) {
        UpdateWrapper<Order> updateWrapper = new UpdateWrapper<Order>();
        updateWrapper.eq("order_id", order.getOrderId());
        Order o = new Order();
        o.setSellerId(order.getSellerId());
        o.setBuyerId(order.getBuyerId());
        //o是要修改的，updateWrapper是条件
        return orderMapper.update(o, updateWrapper);
    }


    /**
     * 删除
     * 注意，这里是根据主键id删除，两个分表如果有相同的id，则都会被删除
     * 测试数据： localhost:9906/order/deleteOrder/8
     */
    @DeleteMapping("/deleteOrder/{id}")
    public int deleteOrderByOrderId(@PathVariable("id") Long id) {
        return orderMapper.deleteById(id);
    }

    /**
     * 根据orderId删除
     * 测试数据：
     * localhost:9906/order/deleteOrderByOrderId?orderId=6
     */
    @DeleteMapping("/deleteOrderByOrderId1")
    public int deleteOrderByOrderId1(@RequestParam("orderId") Long orderId) {
        QueryWrapper<Order> queryWrapper = new QueryWrapper<Order>();
        queryWrapper.eq("order_id", orderId);
        return orderMapper.delete(queryWrapper);
    }


    /**
     * 批量删除   注意这里也是根据主键id删除
     * 测试数据： localhost:9906/order/deleteOrders?ids=57,58,59
     */
    @DeleteMapping("/deleteOrders")
    public int deleteOrders(@RequestParam("ids") List<Long> ids) {
        return orderMapper.deleteBatchIds(ids);
    }

    /**
     * 根据orderId批量删除
     * 测试数据： localhost:9906/order/deleteOrdersByOrderIds?orderIds=16,17,18
     */
    @DeleteMapping("/deleteOrdersByOrderIds")
    public int deleteOrdersByOrderIds(@RequestParam("orderIds") List<Long> orderIds) {
        QueryWrapper<Order> queryWrapper = new QueryWrapper<Order>();
        queryWrapper.in("order_id", orderIds);
        return orderMapper.delete(queryWrapper);
    }


}
