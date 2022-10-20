package org.tfcode.service;

import org.tfcode.dto.Result;
import org.tfcode.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

public interface VoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
