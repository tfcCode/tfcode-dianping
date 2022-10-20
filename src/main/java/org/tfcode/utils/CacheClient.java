package org.tfcode.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.tfcode.entity.Shop;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月20日 08:41:00
 * @Description TODO
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)))
                .build();
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit, Function<ID, R> dbFallback) {
        // 从 Redis 查询商铺缓存
        String shopCacheKey = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(shopCacheKey);
        if (!StringUtils.isEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 从数据库查
        R r = dbFallback.apply(id);
        if (ObjectUtils.isEmpty(r)) {
            redisTemplate.opsForValue().set(shopCacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(shopCacheKey, r, time, timeUnit);

        return r;
    }

    public <R, ID> R queryWithLogicalExpireTime(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit, Function<ID, R> dbFallback) {
        // 从 Redis 查询商铺缓存
        String shopCacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopCache = redisTemplate.opsForValue().get(shopCacheKey);
        if (StringUtils.isEmpty(shopCache)) {
            return null;
        }

        RedisData redisData = BeanUtil.toBean(shopCache, RedisData.class);
        R r = BeanUtil.toBean(((JSONObject) redisData.getData()), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 未过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryGetLock(lockKey);
        if (isLock) {
            threadPoolTaskExecutor.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(shopCacheKey, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    deleteLock(lockKey);
                }
            });
        }
        return r;
    }

    public boolean tryGetLock(String key) {
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return isLock;
    }

    public void deleteLock(String key) {
        redisTemplate.delete(key);
    }

}
