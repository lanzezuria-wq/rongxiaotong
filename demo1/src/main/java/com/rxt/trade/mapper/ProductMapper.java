package com.rxt.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.trade.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 按 id 查商品并<b>加行锁</b>（SELECT ... FOR UPDATE）。
     *
     * <p><b>只有下单扣库存时才用这个方法，普通查询不要用。</b>
     * 行锁会在事务提交前一直持有，用错地方会把并发读全堵住。
     *
     * <p><b>为什么必须加锁？</b>假设库存只剩 1 件，两个买家同时下单：
     * <pre>
     *   线程A: 查库存=1 → 判断够 → 扣减为 0
     *   线程B:      查库存=1 → 判断够 → 扣减为 0      ← 两个人都下单成功，库存卖成 -1
     * </pre>
     * 加了 {@code FOR UPDATE} 之后，线程 B 的查询会阻塞到 A 的事务提交，
     * 此时它读到的是已经扣减后的库存 0，判断"不够"直接失败。
     *
     * <p>注意：这个锁只在<b>事务内</b>有效。调用它的 Service 方法必须带
     * {@code @Transactional}，否则语句执行完锁就释放了，等于没加。
     */
    @Select("SELECT * FROM t_product WHERE id = #{id} FOR UPDATE")
    Product selectByIdForUpdate(@Param("id") Long id);
}
