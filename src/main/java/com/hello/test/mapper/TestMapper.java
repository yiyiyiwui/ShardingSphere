package com.hello.test.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hello.test.pojo.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TestMapper extends BaseMapper<Device> {

}
