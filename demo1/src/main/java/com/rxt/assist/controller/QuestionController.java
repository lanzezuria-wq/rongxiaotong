package com.rxt.assist.controller;

import com.rxt.assist.dto.AnswerDTO;
import com.rxt.assist.dto.QuestionDTO;
import com.rxt.assist.service.ConsultService;
import com.rxt.assist.vo.QuestionVO;
import com.rxt.common.Result;
import com.rxt.security.LoginUser;
import com.rxt.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 在线提问接口 —— M5 专家助力（3 个接口）。
 *
 * <p>提问和预约折成两个 Controller 而不是一个，是因为它们是两个独立的资源
 * （{@code /api/question} 与 {@code /api/appointment}），契约里也分成两组。
 * 合成一个就必须放弃类级 {@code @RequestMapping}、在每个方法上写全路径，
 * 那样既失去一层约束，Swagger 分组也会乱。
 */
@Tag(name = "14. M5 在线提问", description = "农户提问 + 专家回答（3 个接口）")
@RestController
@RequestMapping("/api/question")
@RequiredArgsConstructor
public class QuestionController {

    private final ConsultService consultService;

    @Operation(summary = "提问",
            description = "expertId 可选：填了就是定向提问（只有该专家能答），"
                    + "不填则进入公共池，任何已通过审核的专家都能回答（先到先得）。"
                    + "填了的话服务端会校验该专家确实已通过审核，且不能向自己提问。"
                    + "注意：expertId 是 t_expert.id。")
    @PostMapping
    public Result<Map<String, Long>> ask(@Valid @RequestBody QuestionDTO dto) {
        Long id = consultService.ask(SecurityUtils.currentUserId(), dto);
        return Result.ok("提问已提交，专家回复后会通知您", Map.of("id", id));
    }

    @Operation(summary = "提问列表",
            description = "⚠️ 同一个接口按角色返回不同视角：普通用户看「我提的问题」；"
                    + "专家看「别人点名问我的 + 面向所有专家的公开提问 + 我自己提的」。"
                    + "status 可选（0 待回答 / 1 已回答）。排序：待回答在前。")
    @GetMapping("/list")
    public Result<List<QuestionVO>> list(@RequestParam(required = false) Integer status) {
        LoginUser loginUser = SecurityUtils.currentUser();
        return Result.ok(consultService.myQuestions(loginUser.getUserId(), loginUser.getRole(), status));
    }

    @Operation(summary = "专家回答（仅专家）",
            description = "只能回答一次，重复回答返回 4003；不能回答自己的提问。"
                    + "定向提问只有被指定的专家能答，其它专家返回 1007。"
                    + "公开提问（expertId 为空）任何已通过审核的专家都能答。"
                    + "回答成功会给该专家的「已回答问题数」+1。")
    @PutMapping("/{id}/answer")
    public Result<Void> answer(@PathVariable Long id,
                              @Valid @RequestBody AnswerDTO dto) {
        consultService.answer(SecurityUtils.currentUserId(), id, dto);
        return Result.ok("回答已提交，感谢您的专业解答", null);
    }
}
