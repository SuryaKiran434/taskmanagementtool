package com.suryakiran.taskmanagementtool.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

public class RateLimiterFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterFilter.class);
    private final Bucket bucket;

    public RateLimiterFilter() {
        // Use Bandwidth.simple() with a refill of 10 tokens per minute
        Bandwidth limit = Bandwidth.simple(10, Duration.ofMinutes(1)); // 10 requests per minute
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for IP: {}", request.getRemoteAddr());
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(bucket.getAvailableTokens()));
            httpResponse.getWriter().write("Too many requests - Rate limit exceeded");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void destroy() {
        // No destruction needed
    }
}