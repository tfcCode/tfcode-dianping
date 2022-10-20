package org.tfcode.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.DefaultScriptExecutor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月20日 17:25:00
 * @Description TODO
 */
public class SimpleRedisLock implements Lock {

    /**
     * 业务名称
     */
    private String businessName;
    /**
     * 锁的 key 前缀
     */
    private static final String KEY_PRIFIX = "lock:";

    private static final String VALUE_PRIFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String businessName, StringRedisTemplate redisTemplate) {
        this.businessName = businessName;
        this.redisTemplate = redisTemplate;
    }

    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> UNlOCK_SCRIPT;

    static {
        UNlOCK_SCRIPT = new DefaultRedisScript<>();
        UNlOCK_SCRIPT.setLocation(new ClassPathResource("lua/redis.lua"));
        UNlOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 使用 lua 脚本
     */
    @Override
    public void unlock() {
        String arg = VALUE_PRIFIX + Thread.currentThread().getId();
        redisTemplate.execute(UNlOCK_SCRIPT, Collections.singletonList(KEY_PRIFIX + businessName), arg);
    }

    /*@Override
    public void unlock() {
        String id = VALUE_PRIFIX + Thread.currentThread().getId();
        String keyValue = redisTemplate.opsForValue().get(KEY_PRIFIX + businessName);
        if (id.equalsIgnoreCase(keyValue)) {
            redisTemplate.delete(KEY_PRIFIX + businessName);
        }
    }*/

    @Override
    public boolean tryLock(long timeoutSeconds) {
        // 获取线程标识
        long threadId = Thread.currentThread().getId();
        String lockKey = KEY_PRIFIX + businessName;
        String lockValue = VALUE_PRIFIX + threadId;
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, timeoutSeconds, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(isLock);
    }
}
