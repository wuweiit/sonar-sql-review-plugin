package com.example.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@DS("slave")
public interface MapperWithDsMapper extends BaseMapper<AppUserEntity> {
}
