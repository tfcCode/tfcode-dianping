package org.tfcode.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月20日 10:29:00
 * @Description TODO
 */
@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号位数
     */
    private static final int BITS_COUNT = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 生成唯一 ID：格式
     * 0      0000000000000000000000000000000   000000000000000000000000000000
     * 符号位              时间戳(31位)                          序列号(32位)
     *
     * @param keyPrefix 业务前缀
     * @return
     */
    public long nextId(String keyPrefix) {
        // 获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        // 生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long increment = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timestamp << BITS_COUNT | increment;
    }
}
