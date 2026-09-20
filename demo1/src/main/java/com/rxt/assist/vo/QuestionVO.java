package com.rxt.assist.vo;

import com.rxt.assist.entity.Question;
import com.rxt.common.util.DateUtils;
import lombok.Data;

/**
 * 在线提问出参 —— 对应契约里的 {@code QuestionVO}。
 *
 * <p>契约字段：{@code id / title / content / expertName / answer / status /
 * createdAt / answeredAt}。其中 {@code expertName} 是<b>被指定的专家名</b>，
 * 可为 null（公开提问时没有人被指定）。
 *
 * <p>{@code expertName} 与"实际回答人"可能是两个人：公开提问（expertId=null）下
 * 任何专家都能答，所以本类额外补了 {@code answerByName} —— 契约外字段，
 * 但前端要显示"由 XX 专家回答"就必须有它，否则公开提问的回答会显示成"未知专家"。
 * <b>已登记在契约偏差清单里。</b>
 *
 * <p>两个时间字段：{@code createdAt} 提问时间、{@code answeredAt} 回答时间
 * （未回答时为 null，契约里标了 nullable）。
 */
@Data
public class QuestionVO {

    private Long id;

    private String title;

    private String content;

    /** 被指定回答的专家姓名。公开提问时为 null */
    private String expertName;

    /** 实际回答的专家姓名。未回答时为 null。契约外字段，见类注释 */
    private String answerByName;

    private String answer;

    /** 0 待回答 / 1 已回答 */
    private Integer status;

    private String createdAt;

    private String answeredAt;

    public static QuestionVO from(Question question) {
        if (question == null) {
            return null;
        }
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setTitle(question.getTitle());
        vo.setContent(question.getContent());
        vo.setAnswer(question.getAnswer());
        vo.setStatus(question.getStatus());
        vo.setCreatedAt(DateUtils.format(question.getCreateTime()));
        vo.setAnsweredAt(DateUtils.format(question.getAnsweredAt()));
        // expertName / answerByName 需要 join 两张表，由 Service 批量回填
        return vo;
    }
}
