package com.hello.test.pojo;// Device.java
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("t_device")   // 逻辑表名，永远写这个
@Data
public class Device {

    @TableId(value = "id", type = IdType.ASSIGN_ID)  // ← 必须改成 ASSIGN_ID
    private Long id;                                 // ← 必须改成 Long（原来是 Integer）

    private String deviceId;
    private String deviceName;
    private String roomName;
    private Integer status;
    private Date createTime;

}