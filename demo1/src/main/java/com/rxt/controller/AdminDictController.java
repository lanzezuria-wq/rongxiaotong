package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.DictDTO;
import com.rxt.service.DictService;
import com.rxt.vo.DictVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据字典接口 —— M7（SRS 表41 / 表59），4 个接口。
 *
 * <p><b>⚠️ 这个类的权限配置是全项目最特殊的一处，必须看懂：</b>
 * 类上<b>故意没有</b> {@code @PreAuthorize("hasRole('admin')")}，
 * 而是把管理员校验下沉到"增删改"三个方法上。
 *
 * <p>原因是 SRS 表59 里这四个接口的"认证"列标注不一样：
 * <pre>
 *   GET    /api/admin/dict/{type}   认证 √          ← 没标"管理员"
 *   POST   /api/admin/dict          认证 √（管理员）
 *   PUT    /api/admin/dict/{id}     认证 √（管理员）
 *   DELETE /api/admin/dict/{id}     认证 √（管理员）
 * </pre>
 * 查询接口不限制角色是有道理的：前端所有下拉框（商品分类、资金用途、所属行业、贷款期限）
 * 都要调它，如果只允许管理员，那农户发布商品时连分类都选不了。
 *
 * <p>对应的，{@code SecurityConfig} 里也把
 * {@code GET /api/admin/dict/**} 单独放在 {@code /api/admin/**} 的管理员规则<b>之前</b>，
 * 声明为 {@code authenticated()}。两处必须成对修改——只改一边会出现
 * "接口能访问但下拉框空着"或者"下拉框报 403"这类问题。
 */
@Tag(name = "04. M7 数据字典", description = "字典项查询（所有登录用户）+ 增删改（仅管理员），共 4 个接口")
@RestController
@RequestMapping("/api/admin/dict")
@RequiredArgsConstructor
public class AdminDictController {

    private final DictService dictService;

    @Operation(summary = "按类型查询字典项",
            description = "所有登录用户都能调用（前端下拉框需要）。"
                    + "type 取值：product_category / fund_usage / industry / loan_term")
    @GetMapping("/{type}")
    public Result<List<DictVO>> listByType(@PathVariable String type) {
        return Result.ok(dictService.listByType(type));
    }

    @Operation(summary = "新增字典项", description = "仅管理员。同类型下编码不能重复")
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public Result<DictVO> create(@Valid @RequestBody DictDTO dto) {
        return Result.ok("新增成功", dictService.create(dto));
    }

    @Operation(summary = "编辑字典项", description = "仅管理员。同类型下编码不能重复")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<DictVO> update(@PathVariable Long id,
                                @Valid @RequestBody DictDTO dto) {
        return Result.ok("保存成功", dictService.update(id, dto));
    }

    @Operation(summary = "删除字典项", description = "仅管理员。被业务表引用时不允许删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return Result.ok("删除成功", null);
    }
}
