package org.tfcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.tfcode.dto.LoginFormDTO;
import org.tfcode.dto.Result;
import org.tfcode.entity.User;

import javax.servlet.http.HttpSession;

public interface UserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
