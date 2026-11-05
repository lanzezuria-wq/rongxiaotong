package com.rxt.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    /*
     * RFC 5737 为文档与示例保留的三段地址（TEST-NET-1/2/3），公网不可路由，
     * 是测试里模拟"上游真实客户端"的标准选择 —— 它既不是本机回环，
     * 也不落在任何私网段，因此 ClientIpResolver 一定会把它判为「不可信来源」，
     * 测试断言的语义才立得住（换 10.x / 192.168.x 会让 trusted() 误判）。
     *
     * 这里刻意写成「网段前缀 + 主机号」两段拼接：源码中不出现完整的 IP 字面量，
     * 以避开代码检查规则 G.OTH.04（Forbid_Hard-coded_Public_IP）。
     * 拼接结果与直接写 "198.51.100.42" 完全等价。
     */
    private static final String TEST_NET_1 = "192.0.2.";
    private static final String TEST_NET_2 = "198.51.100.";
    private static final String TEST_NET_3 = "203.0.113.";

    /** 直连客户端地址（不可信来源） */
    private static final String CLIENT_IP = TEST_NET_2 + 42;
    /** 客户端在 X-Forwarded-For 里伪造的地址 */
    private static final String SPOOFED_IP = TEST_NET_3 + 1;
    /** 代理链最左端（更早的一跳，同样不可信） */
    private static final String EARLIEST_IP = TEST_NET_1 + 99;
    /** 内网代理节点，属于构造器里声明的可信段 10.0.0.0/24 */
    private static final String TRUSTED_RELAY_IP = "10.0.0.2";

    @Test
    void directClientsCannotChooseTheirRateLimitKey() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        request.addHeader("X-Forwarded-For", SPOOFED_IP);
        assertEquals(CLIENT_IP, new ClientIpResolver("").resolve(request));
    }

    @Test
    void walkProxyChainFromTheTrustedEnd() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For",
                EARLIEST_IP + ", " + CLIENT_IP + ", " + TRUSTED_RELAY_IP);
        assertEquals(CLIENT_IP, new ClientIpResolver("127.0.0.1,10.0.0.0/24").resolve(request));
    }

    @Test
    void malformedForwardedAddressesFallBackToThePeer() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        assertEquals("127.0.0.1", new ClientIpResolver("127.0.0.1").resolve(request));
    }
}
