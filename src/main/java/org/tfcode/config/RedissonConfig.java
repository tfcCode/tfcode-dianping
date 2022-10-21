package org.tfcode.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月21日 09:00:00
 * @Description TODO
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient getRedissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://47.100.136.186:9999");
        return Redisson.create(config);
    }
}
