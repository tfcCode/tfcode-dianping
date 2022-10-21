package org.tfcode.service.impl;

import org.tfcode.entity.BlogComments;
import org.tfcode.mapper.BlogCommentsMapper;
import org.tfcode.service.BlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

}
