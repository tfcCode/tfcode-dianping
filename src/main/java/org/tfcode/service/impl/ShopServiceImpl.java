package org.tfcode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.tfcode.dto.Result;
import org.tfcode.entity.Shop;
import org.tfcode.mapper.ShopMapper;
import org.tfcode.service.ShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tfcode.utils.CacheClient;
import org.tfcode.utils.RedisConstants;
import org.tfcode.utils.RedisData;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolExecutor;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result selectById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES,
                id2 -> getById(id2));

        // 缓存击穿：互斥锁
        // Shop shop = queryWithMutex(id);

        // 缓存击穿：逻辑过期
        // Shop shop = queryWithLogicalExpireTime(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    public void saveShopToRedis(Long id, Long expierSeconds) {
        Shop shop = getById(id);
        RedisData redisData = RedisData.builder()
                .data(shop)
                .expireTime(LocalDateTime.now().plusSeconds(expierSeconds))
                .build();
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }

    public Shop queryWithLogicalExpireTime(Long id) {
        // 从 Redis 查询商铺缓存
        String shopCacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopCache = redisTemplate.opsForValue().get(shopCacheKey);
        if (StringUtils.isEmpty(shopCache)) {
            return null;
        }

        RedisData redisData = BeanUtil.toBean(shopCache, RedisData.class);
        Shop shop = BeanUtil.toBean(((JSONObject) redisData.getData()), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 未过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryGetLock(lockKey);
        if (isLock) {
            threadPoolExecutor.submit(() -> {
                try {
                    this.saveShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    deleteLock(lockKey);
                }
            });
        }
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        // 从 Redis 查询商铺缓存
        String shopCacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopCache = redisTemplate.opsForValue().get(shopCacheKey);
        if (!StringUtils.isEmpty(shopCache)) {
            return JSONUtil.toBean(shopCache, Shop.class);
        }

        // 从数据库查
        Shop shop = null;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            boolean isLock = tryGetLock(lockKey);
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = baseMapper.selectById(id);
            if (ObjectUtils.isEmpty(shop)) {
                redisTemplate.opsForValue().set(shopCacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            redisTemplate.opsForValue().set(shopCacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            deleteLock(lockKey);
        }
        return shop;
    }

    public Shop queryWithPassThrough(Long id) {
        // 从 Redis 查询商铺缓存
        String shopCacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopCache = redisTemplate.opsForValue().get(shopCacheKey);
        if (!StringUtils.isEmpty(shopCache)) {
            return JSONUtil.toBean(shopCache, Shop.class);
        }

        // 从数据库查
        Shop shop = baseMapper.selectById(id);
        if (ObjectUtils.isEmpty(shop)) {
            redisTemplate.opsForValue().set(shopCacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        redisTemplate.opsForValue().set(shopCacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    public boolean tryGetLock(String key) {
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return isLock;
    }

    public void deleteLock(String key) {
        redisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺 id 不能为空！");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
