package org.tfcode.interceptor;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.tfcode.dto.UserDTO;
import org.tfcode.utils.RedisConstants;
import org.tfcode.utils.UserHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月19日 10:14:00
 * @Description TODO
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (ObjectUtils.isEmpty(UserHolder.getUser())) {
            response.setStatus(401);
            return false;
        }

        return true;
    }
}
