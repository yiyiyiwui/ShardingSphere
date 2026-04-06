package com.hello.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_order")
public class Order {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer orderId;
    private Integer sellerId;
    private Integer buyerId;
}
