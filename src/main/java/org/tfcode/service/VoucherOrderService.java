package org.tfcode.service;

import org.tfcode.dto.Result;
import org.tfcode.entity.SeckillVoucher;
import org.tfcode.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface VoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher);
}
