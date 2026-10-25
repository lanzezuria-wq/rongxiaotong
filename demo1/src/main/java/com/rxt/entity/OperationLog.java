package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String phone;
    private String module;
    private String operation;
    private String method;
    private String uri;
    private String params;
    private String ip;
    private Integer success;
    private String errorMsg;
    private Integer costMs;
    private String requestId;
    private LocalDateTime createTime;
}
