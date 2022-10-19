package org.tfcode.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tfcode.interceptor.LoginInterceptor;
import org.tfcode.interceptor.RefreshTokenInterceptor;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月19日 10:24:00
 * @Description TODO
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).addPathPatterns("/**").order(0);
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/login", "/user/code", "/blog/hot", "/shop/**", "/shop-type/**", "/upload/**", "/voucher/**").order(10);
    }
}
