package com.fin.commons.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从请求头解析当前用户上下文 (在 TraceFilter 之后执行)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String userId = req.getHeader(UserContext.HEADER_USER_ID);
            if (userId != null && !userId.isEmpty()) {
                UserContext.setUserId(Long.parseLong(userId));
            }
            UserContext.setDevice(req.getHeader(UserContext.HEADER_DEVICE));
            UserContext.setRoles(req.getHeader(UserContext.HEADER_ROLES));
            UserContext.setTenant(req.getHeader(UserContext.HEADER_TENANT));
            chain.doFilter(req, resp);
        } finally {
            UserContext.clear();
        }
    }
}
