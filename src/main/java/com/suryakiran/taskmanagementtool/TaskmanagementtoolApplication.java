package com.suryakiran.taskmanagementtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.suryakiran.taskmanagementtool")
@EnableCaching
public class TaskmanagementtoolApplication {

	private static final Logger logger = LoggerFactory.getLogger(TaskmanagementtoolApplication.class);

	public static void main(String[] args) {
		logger.info("Starting Task Management Tool Application");
		SpringApplication.run(TaskmanagementtoolApplication.class, args);
		logger.info("Task Management Tool Application Started");
	}
}