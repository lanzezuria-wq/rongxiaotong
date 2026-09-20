package com.rxt.trade.vo;

import com.rxt.trade.entity.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 收货地址出参 —— 对应契约里的 AddressVO。
 *
 * <p>比契约多一个 {@code fullAddress}（省市区 + 详细地址拼好的整串）。
 * 前端三个地方要显示完整地址（购物车结算、订单确认、订单详情），
 * 每处都拼一遍既啰嗦又容易漏掉某一个字段，后端拼好最省事。
 *
 * <p>拼接时用 {@code Stream} 过滤掉空白段，这样"省市区都没填、只填了详细地址"
 * 的情况不会拼出 {@code "  -  -  XX路1号"} 这种带空格的怪结果。
 */
@Data
@Schema(description = "收货地址")
public class AddressVO {

    private Long id;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detail;

    /** 是否默认地址：0 否 / 1 是 */
    private Integer isDefault;

    /** 完整地址，例：湖北省武汉市洪山区珞喻路 1037 号 */
    private String fullAddress;

    public static AddressVO from(Address address) {
        if (address == null) {
            return null;
        }
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiver(address.getReceiver());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        vo.setFullAddress(joinAddress(address));
        return vo;
    }

    private static String joinAddress(Address address) {
        return Stream.of(address.getProvince(), address.getCity(),
                        address.getDistrict(), address.getDetail())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining());
    }
}
