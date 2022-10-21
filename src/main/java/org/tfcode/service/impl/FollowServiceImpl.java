package org.tfcode.service.impl;

import org.tfcode.entity.Follow;
import org.tfcode.mapper.FollowMapper;
import org.tfcode.service.FollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

}
