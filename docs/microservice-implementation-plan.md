# YoungPlace Microservice Implementation Plan

## Context and constraints

- Runtime: JDK 8
- Framework: Spring Boot + Spring Cloud
- Services: registry, gateway, iam-service, user-service, common
- Delivery mode: plan -> implement -> verify for each task

## Fixed execution workflow

### Daily 15-minute loop

1. Task split (5 min)
2. AI closed loop: plan -> implement -> verify (5 min)
3. Pre-commit quality check: bug/edge/regression (5 min)

### KPI actions

- Volume: complete small, reviewable tasks.
- Engage: keep high-context prompts (errors/logs/constraints).
- Efficiency: test first, then change, then diff review.
- Growth: improve 1-2 weak indicators per week.
- Breadth: cover multiple modules in one week.

## Task breakdown (this round)

### T1 Parent and module skeleton

- **Plan**: Create parent pom and module poms with managed versions.
- **Implement**: Add Maven multi-module structure.
- **Verify**: `mvn -q -DskipTests validate` at repo root.
- **DoD**: Build graph is valid and modules resolve.

### T2 Registry service

- **Plan**: Add Eureka server for service discovery.
- **Implement**: Application class + application.yml.
- **Verify**: Service starts on port 8761.
- **DoD**: Gateway and business services can register to registry.

### T3 Gateway service

- **Plan**: Add API gateway and route rules.
- **Implement**: Gateway app + route config for iam and user services.
- **Verify**: Requests route by path through gateway.
- **DoD**: `/api/iam/**` and `/api/users/**` route through gateway.

### T4 IAM service

- **Plan**: Provide minimal login endpoint with JWT.
- **Implement**: Login controller and request DTO.
- **Verify**: Valid credentials return token, invalid return auth error.
- **DoD**: Stable JSON response contract and input validation.

### T5 User service

- **Plan**: Provide baseline user query API.
- **Implement**: User controller with sample response.
- **Verify**: Query works directly and through gateway.
- **DoD**: Consistent response structure with common module.
