# Task Management Tool

## Overview

The Task Management Tool is a Spring Boot application designed to manage tasks efficiently. It provides a RESTful API for task management, user authentication, and authorization. The application is built using Java, Spring Boot, and Maven, and it includes features such as caching, security, and API documentation.

## Features

- **Task Management**: Create, read, update, and delete tasks.
- **User Authentication and Authorization**: Secure endpoints with role-based access control.
- **API Documentation**: Interactive API documentation using OpenAPI.
- **Caching**: Improve performance with caching mechanisms.
- **Profile-based Configuration**: Different configurations for different environments (e.g., desktop profile).

## Technologies Used

- **Java**: Programming language.
- **Spring Boot**: Framework for building the application.
- **Spring Security**: For authentication and authorization.
- **Spring Cache**: For caching support.
- **OpenAPI**: For API documentation.
- **Maven**: Build and dependency management tool.

## Configuration

The application can be configured using the `application.properties` file located in the `src/main/resources` directory. You can set different profiles and configurations as needed.

### Profiles

The application supports different profiles for different environments. For example, the `desktop` profile allows all requests without authentication.

## API Documentation

The API documentation is available at `/swagger-ui.html` once the application is running. It provides an interactive interface to explore and test the API endpoints.

## Security

The application uses Spring Security for authentication and authorization. It supports role-based access control with the following roles:

- **ADMIN**: Access to `/admin/**` endpoints.
- **USER**: Access to `/user/**` endpoints.

### Password Encoding

Passwords are encoded using `BCryptPasswordEncoder`.

## Caching

Caching is enabled using Spring Cache. It helps improve the performance of the application by storing frequently accessed data in memory.

## Logging

The application uses SLF4J with Logback for logging. Logs are configured in the `logback-spring.xml` file.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

## Contact

For any questions or support, please contact [SuryaKiran](https://github.com/SuryaKiran434).