package com.example.aitools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aitools.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
