package com.suryakiran.taskmanagementtool.config;

import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers converters so that @RequestParam values like "To-Do" and "In Progress"
 * are mapped to Status/Priority enums using the same case-insensitive display-name
 * logic as the @JsonCreator in each enum.
 */
@Configuration
public class EnumConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Status.class, Status::fromValue);
        registry.addConverter(String.class, Priority.class, Priority::fromValue);
    }
}
