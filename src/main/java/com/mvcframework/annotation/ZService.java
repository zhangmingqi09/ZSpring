package com.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * @Description: TODO
 * @Author: zhang
 * @Date: 2020/7/14 17:23
 * @Version: V1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZService {
    String value() default "";
}
