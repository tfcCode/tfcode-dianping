package org.tfcode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.tfcode.dto.Result;
import org.tfcode.dto.UserDTO;
import org.tfcode.entity.Blog;
import org.tfcode.entity.User;
import org.tfcode.mapper.BlogMapper;
import org.tfcode.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tfcode.service.UserService;
import org.tfcode.utils.RedisConstants;
import org.tfcode.utils.SystemConstants;
import org.tfcode.utils.UserHolder;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (ObjectUtils.isEmpty(blog)) {
            return Result.fail("博客不存在！");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 是否已经点赞
     *
     * @param blog
     */
    public void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (ObjectUtils.isEmpty(user)) {
            return;
        }
        String blogKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = redisTemplate.opsForZSet().score(blogKey, user.getId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        String blogKey = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = redisTemplate.opsForZSet().score(blogKey, user.getId().toString());
        if (ObjectUtils.isEmpty(score)) {
            // 未点赞
            LambdaUpdateWrapper<Blog> updateWrapper = new UpdateWrapper<Blog>().lambda()
                    .eq(Blog::getId, id)
                    .setSql("liked = liked + 1");
            boolean isSuccess = update(updateWrapper);
            if (isSuccess) {
                // zadd key value score
                redisTemplate.opsForZSet().add(blogKey, user.getId().toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞，取消点赞
            LambdaUpdateWrapper<Blog> updateWrapper = new UpdateWrapper<Blog>().lambda()
                    .eq(Blog::getId, id)
                    .setSql("liked = liked - 1");
            update(updateWrapper);
            redisTemplate.opsForZSet().remove(blogKey, user.getId().toString());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 查询 top5 的点赞用户列表
        String blogKey = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> range = redisTemplate.opsForZSet().range(blogKey, 0, 4);
        if (ObjectUtils.isEmpty(range)) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据 id 查用户
        LambdaQueryWrapper<User> queryWrapper = new QueryWrapper<User>().lambda()
                .in(User::getId, ids)
                .last("order by field( " + StrUtil.join(",", ids) + ")");
        List<UserDTO> users = userService.list(queryWrapper)
                .stream().map(user -> BeanUtil.toBean(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(users);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
