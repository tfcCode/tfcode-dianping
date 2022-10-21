package org.tfcode.service;

import org.tfcode.dto.Result;
import org.tfcode.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface BlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);
}
