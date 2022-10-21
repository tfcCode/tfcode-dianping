package org.tfcode.service.impl;

import org.tfcode.entity.UserInfo;
import org.tfcode.mapper.UserInfoMapper;
import org.tfcode.service.UserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

}
