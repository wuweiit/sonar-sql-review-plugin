package com.example.service;

import com.baomidou.dynamic.datasource.annotation.DS;

@DS("main")
public class MixedDs {
    @DS("order")
    public void queryOrder() {
        // method-level overrides class-level
    }

    public void queryDefault() {
        // uses class-level "main"
    }
}
