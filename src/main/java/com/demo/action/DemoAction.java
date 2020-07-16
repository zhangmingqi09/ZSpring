package com.demo.action;

import com.demo.service.IDemoService;
import com.mvcframework.annotation.ZAutowired;
import com.mvcframework.annotation.ZController;
import com.mvcframework.annotation.ZRequestMapping;
import com.mvcframework.annotation.ZRequestParameter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Description: TODO
 * @Author: zhang
 * @Date: 2020/7/14 17:20
 * @Version: V1.0
 */
@ZController
@ZRequestMapping("/demo")
public class DemoAction {

    @ZAutowired
    private IDemoService demoService;

    @ZRequestMapping("/hello")
    public void hello(HttpServletRequest req, HttpServletResponse resp, @ZRequestParameter("name") String name) {
        try {
            resp.getWriter().write("Hello, " + demoService.getName(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}