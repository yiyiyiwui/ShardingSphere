package com.hello.test.controller;


import com.hello.test.mapper.TestMapper;
import com.hello.test.pojo.Device;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/get")
@Slf4j
public class TestController {

    @Autowired
    private TestMapper testMapper;

    /**
     * 查询
     */
    @RequestMapping("/List")
    public List<Device> getList() {
        return testMapper.selectList(null);
    }


    /**
     * 新增
     */
    @RequestMapping("/save")
    public void getSave() {
        try {
            Device device = new Device();
            device.setDeviceId("DEV-006");
            device.setDeviceName("核心交换机-A");
            device.setRoomName("北京一号机房");
            device.setStatus(1);
            // id 不需要手动填，ShardingSphere 自动生成雪花ID
            log.info("插入数据：{}",device);
            int rows = testMapper.insert(device);
            log.info("插入成功，受影响行数: {}", rows);
        } catch (Exception e) {
            log.info("插入失败：{}",e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
