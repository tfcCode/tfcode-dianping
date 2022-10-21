package org.tfcode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.tfcode.dto.Result;
import org.tfcode.entity.SeckillVoucher;
import org.tfcode.entity.VoucherOrder;
import org.tfcode.mapper.VoucherOrderMapper;
import org.tfcode.service.SeckillVoucherService;
import org.tfcode.service.VoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tfcode.utils.RedisIdWorker;
import org.tfcode.utils.UserHolder;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Autowired
    private SeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;


    private VoucherOrderService proxy;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private BlockingQueue<VoucherOrder> orderTasks = new LinkedBlockingQueue<>(1024 * 1024);

    @PostConstruct
    public void init() {
        String queueName = "stream.orders";
        threadPoolTaskExecutor.submit(() -> {
            while (true) {
                try {
                    // 读取消息队列中的消息：xreadgroup group g1 c1 count 1 block 2000 streams streams.order >
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2L)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()));
                    if (CollectionUtils.isEmpty(records)) {
                        continue;
                    }
                    Map<Object, Object> objectMap = records.get(0).getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(objectMap, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    // 消息确认
                    redisTemplate.opsForStream().acknowledge(queueName, "g1", records.get(0).getId());
                } catch (Exception e) {
                    log.info("消息处理异常", e);
                    handlePendingList();
                }
            }
        });
    }

    public void handlePendingList() {
        while (true) {
            try {
                // 读取 pending-list 中的消息：xreadgroup group g1 c1 count 1 block 2000 streams streams.order >
                List<MapRecord<String, Object, Object>> pendingList = redisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0")));
                if (CollectionUtils.isEmpty(pendingList)) {
                    break;
                }
                Map<Object, Object> objectMap = pendingList.get(0).getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(objectMap, new VoucherOrder(), true);
                handleVoucherOrder(voucherOrder);
                // 消息确认
                redisTemplate.opsForStream().acknowledge("stream.orders", "g1", pendingList.get(0).getId());
            } catch (Exception e) {
                log.info("消息处理异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        proxy.createVoucherOrder(voucherOrder);

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(voucherId));
        // 返回 0 代表成功
        if (result.intValue() != 0) {
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
        }

        // 获取代理对象
        proxy = (VoucherOrderService) AopContext.currentProxy();

        return Result.ok(0);
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 判断是否已经买过
        Long userId = voucherOrder.getUserId();
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new QueryWrapper<VoucherOrder>().lambda()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId());
        int count = this.count(queryWrapper);
        if (count > 0) {
            log.error("您已经购买过该优惠券，请勿重复购买！");
            return;
        }

        // 扣减库存
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                // 乐观锁解决多线程并发问题，更新时比较库存，是否与查询时一致
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock -1");

        boolean update = seckillVoucherService.update(updateWrapper);
        if (!update) {
            log.error("秒杀库存不足");
            return;
        }

        // 创建订单
        save(voucherOrder);
    }
}
