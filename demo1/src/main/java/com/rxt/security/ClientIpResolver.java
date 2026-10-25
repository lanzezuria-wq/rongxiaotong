package com.rxt.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${rxt.security.trusted-proxies:}") String proxies) {
        trustedProxies = Arrays.stream(proxies.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!trusted(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return peer;
        // 从直连代理向外剥离可信节点，停在首个不可信来源，不能直接信任最左项。
        String[] chain = forwarded.split(",");
        String current = peer;
        for (int i = chain.length - 1; i >= 0 && trusted(current); i--) {
            String candidate = chain[i].trim();
            if (candidate.length() > 45 || !candidate.matches("[0-9a-fA-F:.]+")) return peer;
            try {
                new IpAddressMatcher(candidate);
            } catch (IllegalArgumentException e) {
                return peer;
            }
            current = candidate;
        }
        return current;
    }

    private boolean trusted(String address) {
        return address != null && trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }
}
