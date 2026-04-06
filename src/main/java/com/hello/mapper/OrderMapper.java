package com.hello.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hello.pojo.Order;

import java.util.List;

public interface OrderMapper extends BaseMapper<Order> {
    void in( int i);
    List<Order> getId(int i);
}
