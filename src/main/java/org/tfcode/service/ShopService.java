package org.tfcode.service;

import org.tfcode.dto.Result;
import org.tfcode.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ShopService extends IService<Shop> {

    Result selectById(Long id);

    Result update(Shop shop);
}
