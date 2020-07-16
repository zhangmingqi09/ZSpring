package com.mvcframework.servlet;

import com.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description: TODO
 * @Author: zhang
 * @Date: 2020/7/14 16:17
 * @Version: V1.0
 */
public class ZDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private List<Handler> handlermapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatcher(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatcher(req, resp);
    }

    public void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Handler handler = getHandler(req);
        if (handler == null) {
            resp.getWriter().write("404 Not Found!");
            return;
        }

        Class<?>[] paramTypes = handler.method.getParameterTypes();
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
            if (handler.paramIndexMapping.containsKey(entry.getKey())) {
                int index = handler.paramIndexMapping.get(entry.getKey());
                paramValues[index] = convert(paramTypes[index], value);
            }
        }
        Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        if (reqIndex != null) {
            paramValues[reqIndex] = req;
        }
        Integer respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        if (respIndex != null) {
            paramValues[respIndex] = resp;
        }

        try {
            handler.method.invoke(handler.instance, paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }



    @Override
    public void init(ServletConfig config) throws ServletException {
        // 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfig"));

        // 扫描需要关联的类
        doScanner(contextConfig.getProperty("scanPackage"));

        // 初始化相关联的类并放入ioc容器中
        doInstance();

        // 执行依赖注入，对AutoWired注解的属性赋值
        doAutoWired();

        // 构造HandlerMapping，将URL和Methos关联
        initHandlerMapping();
    }


    private void initHandlerMapping() {
        if (ioc.isEmpty()) {return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            String baseUrl = "";
            if (clazz.isAnnotationPresent(ZRequestMapping.class)) {
                baseUrl = clazz.getAnnotation(ZRequestMapping.class).value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(ZRequestMapping.class)) {
                    String regex = method.getAnnotation(ZRequestMapping.class).value().trim();
                    if (!"".equals(regex)) {
                        regex = baseUrl + regex;
                    }
                    handlermapping.add(new Handler(entry.getValue(), method, Pattern.compile(regex)));
                }
            }
        }

    }

    private void doAutoWired() {
        if (ioc.isEmpty()) { return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(ZAutowired.class)) {
                    String beanName = field.getAnnotation(ZAutowired.class).value().trim();
                    if ("".equals(beanName)) {
                        beanName = lowerFirstCase(field.getType().getName());
                    }
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }

            }
        }


    }

    private void doInstance() {
        if (classNames.isEmpty()) {return;}
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                Object instance = null;
                if (!clazz.isInterface()) {
                    instance = clazz.newInstance();
                }
                if (clazz.isAnnotationPresent(ZController.class)) {
                    String beanName = lowerFirstCase(clazz.getName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(ZService.class)) {
                    if (clazz.isAnnotationPresent(ZService.class)) {
                        String beanName = clazz.getAnnotation(ZService.class).value();
                        if ("".equals(beanName.trim())) {
                            beanName = lowerFirstCase(clazz.getName());
                        }
                        ioc.put(beanName, instance);

                        Class<?>[] interfaces = clazz.getInterfaces();
                        for (Class<?> anInterface : interfaces) {
                            ioc.put(lowerFirstCase(anInterface.getName()), instance);
                        }
                    }
                } else {
                    continue;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void doScanner(String basePackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file: dir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(basePackage + "." + file.getName());
            } else {
                String className = basePackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }


    }

    private void doLoadConfig(String location) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
//        chars[0] += 32;
        return String.valueOf(chars);
    }

    private Handler getHandler(HttpServletRequest req) {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for (Handler handler : handlermapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (matcher.matches()) {
                return handler;
            }
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }


    private class Handler {
        protected Object instance;
        protected Method method;
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;

        public Handler(Object instance, Method method, Pattern pattern) {
            this.instance = instance;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            // 提取所有方法中加了注解的参数
            Annotation[][] annotationss = method.getParameterAnnotations();
            for (int i = 0; i < annotationss.length; i++) {
                for (Annotation annotation : annotationss[i]) {
                    if (annotation instanceof ZRequestParameter) {
                        String paramName = ((ZRequestParameter) annotation).value().trim();
                        if (!"".equals(paramName)) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            // 提取方法中 HttpServletRequest和HttpServletResponse
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }
    }


}