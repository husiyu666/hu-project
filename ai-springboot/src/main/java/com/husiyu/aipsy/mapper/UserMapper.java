package com.husiyu.aipsy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.husiyu.aipsy.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
