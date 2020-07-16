package com.demo.service.impl;

import com.demo.service.IDemoService;
import com.mvcframework.annotation.ZService;

/**
 * @Description: TODO
 * @Author: zhang
 * @Date: 2020/7/15 13:56
 * @Version: V1.0
 */
@ZService
public class DemoService implements IDemoService {
    @Override
    public String getName(String name) {
        return name;
    }
}