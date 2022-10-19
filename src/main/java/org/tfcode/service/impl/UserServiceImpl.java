package org.tfcode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.tfcode.dto.LoginFormDTO;
import org.tfcode.dto.Result;
import org.tfcode.dto.UserDTO;
import org.tfcode.entity.User;
import org.tfcode.mapper.UserMapper;
import org.tfcode.service.UserService;
import org.springframework.stereotype.Service;
import org.tfcode.utils.RedisConstants;
import org.tfcode.utils.RegexUtils;
import org.tfcode.utils.SystemConstants;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号无效");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送短信验证码成功: {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号无效");
        }
        String cacheCode = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if (StringUtils.isEmpty(cacheCode) || !cacheCode.equalsIgnoreCase(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }

        // 根据手机号查询用户，判断是否是新用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, loginForm.getPhone());
        User user = baseMapper.selectOne(queryWrapper);
        if (ObjectUtils.isEmpty(user)) {
            user = createUserWithPhone(loginForm.getPhone());
        }

        // 将用户信息保存到 Redis 中
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .ignoreNullValue()
                        .setFieldValueEditor((name, value) -> value.toString()));

        String userTokenKey = RedisConstants.LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(userTokenKey, userMap);
        redisTemplate.expire(userTokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    /**
     * 根据手机号创建用户
     *
     * @param phone 用户手机号
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = User.builder()
                .phone(phone)
                .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(8))
                .build();
        save(user);
        return user;
    }
}
