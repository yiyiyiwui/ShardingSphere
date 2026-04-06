package com.hello.shardingsphere;

import com.hello.mapper.OrderMapper;
import com.hello.pojo.Order;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class ShardingSphereApplicationTests {

    @Resource
    private OrderMapper orderMapper;

    //查询
    @Test
    void contextLoads() {
      List<Order> a =  orderMapper.getId(15);
        System.err.println(">>>>>"+a);
    }

    @Test
    void contextLoads1() {
        for (int i = 1; i <= 50; i++) {
//            Order order = new Order();
//            order.setSellerId(1+i);
//            order.setBuyerId(2+i);
            orderMapper.in(i);
//            orderMapper.insert(order);
        }
    }

}
