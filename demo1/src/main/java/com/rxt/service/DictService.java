package com.rxt.service;

import com.rxt.dto.DictDTO;
import com.rxt.vo.DictVO;

import java.util.List;

/**
 * 数据字典服务 —— M7 表41。
 *
 * <p>四个方法对应表59 里字典的四个接口（{@code GET /{type}}、{@code POST}、
 * {@code PUT /{id}}、{@code DELETE /{id}}）。
 *
 * <p>这个模块的规则和用户模块完全不同，所以单独成一个 Service：
 * <ul>
 *   <li><b>类型白名单</b> —— 只能是四个固定类型（{@code DictConst}）；</li>
 *   <li><b>同类型内编码唯一</b> —— 数据库有 {@code uk_dict_type_code} 唯一索引，
 *       所以查重时必须<b>同时</b>带 type 和 code，只查 code 会把别类型下的同名编码误判成冲突；</li>
 *   <li><b>被引用不可删</b> —— 表41 异常处理要求；</li>
 *   <li>删除是<b>物理删除</b>（t_dict 没有 deleted 列），没有回收站语义。</li>
 * </ul>
 */
public interface DictService {

    /** 按类型查字典项，按 sort 升序返回。类型非法时抛 1001 */
    List<DictVO> listByType(String type);

    /** 新增字典项。类型非法或同类型编码重复时抛 1001 */
    DictVO create(DictDTO dto);

    /** 编辑字典项。不存在抛 1008；类型非法或编码冲突抛 1001 */
    DictVO update(Long id, DictDTO dto);

    /** 删除字典项。不存在抛 1008；被业务表引用时抛 1001 "该字典项正在被使用，无法删除" */
    void delete(Long id);
}
