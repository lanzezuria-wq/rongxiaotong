package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM t_user WHERE phone = #{phone} AND deleted = 0 FOR UPDATE")
    User selectByPhoneForUpdate(@Param("phone") String phone);

    @Select("SELECT * FROM t_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
