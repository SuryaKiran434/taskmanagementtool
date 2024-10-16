package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.filter.RateLimiterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.suryakiran.taskmanagementtool")
@EnableCaching
public class TaskmanagementtoolApplication {

	private static final Logger logger = LoggerFactory.getLogger(TaskmanagementtoolApplication.class);

	public static void main(String[] args) {
		logger.info("Starting Task Management Tool Application");
		SpringApplication.run(TaskmanagementtoolApplication.class, args);
		logger.info("Task Management Tool Application Started");
	}

	@Bean
	public FilterRegistrationBean<RateLimiterFilter> rateLimiterFilter() {
		FilterRegistrationBean<RateLimiterFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new RateLimiterFilter());
		registrationBean.addUrlPatterns("/login");
		return registrationBean;
	}
}