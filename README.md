
# Task Management Tool

This repository contains a Spring Boot application for a Task Management Tool, designed to handle user authentication, task creation, management, and user roles in a secure and efficient manner. The system integrates JWT authentication, rate limiting, and offers API documentation through OpenAPI. It is tailored for both general users and admin roles.

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Getting Started](#getting-started)
5. [Configuration](#configuration)
6. [Endpoints](#endpoints)
7. [Security](#security)
8. [Error Handling](#error-handling)
9. [Testing](#testing)

## Features

- **JWT Authentication**: Secure login and token generation for users.
- **Rate Limiting**: Limits API access to prevent abuse.
- **Task Management**: CRUD operations for tasks with filters based on status and priority.
- **User Role Management**: Supports multiple roles, including users and administrators.
- **OpenAPI Integration**: Auto-generated API documentation using Swagger UI.
- **Exception Handling**: Comprehensive error management with custom exceptions.

## Architecture

This application uses a layered architecture:
- **Controllers**: Handle HTTP requests and map them to service logic.
- **Services**: Business logic implementation.
- **Repositories**: Database interactions using JPA.
- **Models**: Define data entities such as `Task`, `User`, and `Role`.
- **Filters**: Implement request handling features such as JWT filtering and rate limiting.
- **Configuration**: Manages Spring and security configurations.

### Technologies Used:
- **Spring Boot**: Framework for building the application.
- **Spring Security**: Used for securing the endpoints with JWT and role-based access control.
- **Spring Data JPA**: Handles database operations.
- **MySQL**: The primary database for persistence.
- **Swagger/OpenAPI**: API documentation.

## Project Structure

```
src/
├── main/
│   ├── java/com/suryakiran/taskmanagementtool/
│   │   ├── config/             # Spring configurations (Database, OpenAPI, Security)
│   │   ├── controller/         # Controllers for handling API requests
│   │   ├── dto/                # Data Transfer Objects (DTOs)
│   │   ├── exception/          # Custom exception handling
│   │   ├── filter/             # Filters for JWT and Rate Limiting
│   │   ├── model/              # Entity classes for User, Task, Role, etc.
│   │   ├── repository/         # JPA repositories for database interactions
│   │   ├── service/            # Business logic services
│   │   └── util/               # Utility classes (JWT, Password validation)
│   └── resources/
│       └── application.yml     # Application configurations
└── test/                       # Unit and Integration tests
```

## Getting Started

### Prerequisites

Ensure you have the following installed:
- **Java 17**
- **Maven**
- **MySQL**

### Installation Steps

1. **Clone the repository**:
   ```
   git clone https://github.com/suryakiran/taskmanagementtool.git
   cd taskmanagementtool
   ```

2. **Configure the database**:
   Edit `application.yml` to set your MySQL database credentials:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/taskmanager_db
       username: <your-db-username>
       password: <your-db-password>
   ```

3. **Run the application**:
   ```
   mvn spring-boot:run
   ```

4. **Access the application**:
   The API will be available at `http://localhost:8080`, and Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Configuration

### Profiles
The application supports different profiles:
- **desktop**: Default profile for local development.
- **dev**: For development environment on port 8081.
- **prod**: For production environment on port 8082.

To activate a profile, use the `spring.profiles.active` property in your `application.yml` or via command line:
```
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Endpoints

### Public Endpoints
- **POST /api/authenticate**: Authenticate a user and return a JWT.
- **POST /api/refresh-token**: Refresh JWT.
- **POST /api/users/register**: Register a new user.

### Task Management
- **GET /api/tasks**: Get all tasks.
- **POST /api/tasks**: Create a new task.
- **GET /api/tasks/{id}**: Retrieve a specific task by ID.
- **PUT /api/tasks/{id}**: Update a task.
- **DELETE /api/tasks/{id}**: Delete a task.

### User Management
- **GET /api/users**: Get all users (Admin only).
- **POST /api/users/login**: Log in as a user.

## Security

- **JWT Authentication**: Users must authenticate to access protected endpoints.
- **Role-based Access Control**: Admin users have extra privileges for managing users and roles.

### Rate Limiting
The application uses **Bucket4j** to limit the number of API calls:
- 10 requests per minute to authentication endpoints (`/api/authenticate`, `/api/refresh-token`).

### Password Requirements:
- Minimum 8 characters
- At least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character.

## Error Handling

Custom exceptions are thrown for various failure cases, such as:
- **AuthenticationFailedException**: Thrown when login credentials are invalid.
- **ResourceNotFoundException**: Thrown when a requested entity is not found.
- **NoTasksFoundException**: Thrown when no tasks match the criteria.

Global exception handlers return meaningful HTTP status codes like `404 NOT FOUND` or `401 UNAUTHORIZED`.

## Testing

Unit and integration tests are written using **JUnit 5** and **Mockito**. Key test cases:
- User authentication and JWT validation.
- CRUD operations for tasks.
- Custom exception handling.

Run the tests with:
```
mvn test
```
