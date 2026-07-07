package com.example.service;

import com.baomidou.dynamic.datasource.annotation.DS;

public class MethodLevelDs {
    @DS("read")
    public List<Order> queryOrders() {
        return null;
    }

    @DS("write")
    public void saveOrder(Order order) {
        // write method
    }

    public void noAnnotation() {
        // no DS
    }
}
