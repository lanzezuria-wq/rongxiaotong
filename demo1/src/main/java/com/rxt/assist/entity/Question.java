package com.rxt.assist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线提问 —— 对应 t_question（M5）。
 *
 * <p><b>{@code expertId} 可以为 null</b>，这是契约明确要求的能力
 * （{@code POST /api/question} 的 expertId 描述是"可选，不填则所有专家可见"）。
 * 它带来一处业务规则上的分支，回答接口必须同时处理两种情形：
 * <ul>
 *   <li>{@code expertId != null} —— 只有这个专家能回答（定向提问）；</li>
 *   <li>{@code expertId == null} —— 任何已通过审核的专家都能回答（公开提问，先到先得）。</li>
 * </ul>
 *
 * <p>{@code answerBy} 记录"实际是谁答的"——公开提问下 {@code expertId} 是 null，
 * 它才是唯一能追溯"谁答的"的字段。它和"被指定的专家"是两个不同的概念，
 * 所以 VO 里对应两个不同的出参（expertName / answerByName）。
 */
@Data
@TableName("t_question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提问人 t_user.id */
    private Long userId;

    /** 指定专家 t_expert.id，null 表示面向所有专家 */
    private Long expertId;

    private String title;

    private String content;

    /** 专家回答内容，未回答时为 null */
    private String answer;

    /** 回答专家的 t_expert.id */
    private Long answerBy;

    /** 0 待回答 / 1 已回答 */
    private Integer status;

    private LocalDateTime answeredAt;

    private LocalDateTime createTime;
}
