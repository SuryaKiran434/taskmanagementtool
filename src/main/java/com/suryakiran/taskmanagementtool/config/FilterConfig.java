package com.suryakiran.taskmanagementtool.config;

import com.suryakiran.taskmanagementtool.filter.RateLimiterFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimiterFilter> customRateLimiterFilter() {
        FilterRegistrationBean<RateLimiterFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RateLimiterFilter());
        registrationBean.addUrlPatterns("/api/authenticate", "/api/refresh-token");
        registrationBean.setOrder(1); // Set the order of the filter
        return registrationBean;
    }
}