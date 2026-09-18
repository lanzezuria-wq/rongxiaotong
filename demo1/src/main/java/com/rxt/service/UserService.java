package com.rxt.service;

import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.dto.UpdateInfoDTO;
import com.rxt.dto.UpdatePasswordDTO;
import com.rxt.vo.LoginVO;
import com.rxt.vo.RegisterVO;
import com.rxt.vo.UserVO;


public interface UserService {


    RegisterVO register(RegisterDTO dto);


    LoginVO login(LoginDTO dto);


    UserVO getInfo(Long userId);


    UserVO updateInfo(Long userId, UpdateInfoDTO dto);


    void changePassword(Long userId, UpdatePasswordDTO dto);
}
