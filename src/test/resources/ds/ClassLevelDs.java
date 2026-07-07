package com.example.service;

import com.baomidou.dynamic.datasource.annotation.DS;

@DS("order")
public class ClassLevelDs {
    public void queryData() {
        // class-level @DS
    }
}
