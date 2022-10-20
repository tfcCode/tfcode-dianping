package org.tfcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.tfcode.dto.Result;
import org.tfcode.entity.SeckillVoucher;
import org.tfcode.entity.VoucherOrder;
import org.tfcode.mapper.VoucherOrderMapper;
import org.tfcode.service.SeckillVoucherService;
import org.tfcode.service.VoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tfcode.utils.RedisIdWorker;
import org.tfcode.utils.SimpleRedisLock;
import org.tfcode.utils.UserHolder;

import java.time.LocalDateTime;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Autowired
    private SeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询秒杀优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("秒杀库存不足");
        }

        // 先获取锁，然后提交事务，再释放锁
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock redisLock = new SimpleRedisLock("order" + userId, redisTemplate);
        boolean isLock = redisLock.tryLock(2000L);
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }

        try {
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, seckillVoucher);
        } finally {
            redisLock.unlock();
        }
    }

    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        // 判断是否已经买过
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new QueryWrapper<VoucherOrder>().lambda()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId);
        int count = this.count(queryWrapper);
        if (count > 0) {
            return Result.fail("您已经购买过该优惠券，请勿重复购买！");
        }

        // 扣减库存
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SeckillVoucher::getVoucherId, voucherId)
                // 乐观锁解决多线程并发问题，更新时比较库存，是否与查询时一致
                .gt(SeckillVoucher::getStock, 0)
                .set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1);

        boolean update = seckillVoucherService.update(updateWrapper);
        if (!update) {
            return Result.fail("秒杀库存不足");
        }

        // 创建订单
        long orderId = redisIdWorker.nextId("order");
        log.info("订单ID: {}", orderId);
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
