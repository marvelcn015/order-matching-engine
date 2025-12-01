# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

High-Performance Order Matching Engine - A Spring Boot application for cryptocurrency exchange order matching.

**Tech Stack:**
- Spring Boot 3.5.8
- Java 17
- MyBatis for data persistence
- MySQL database
- Maven build system

**Package Structure:**
- Base package: `cex.crypto.trading`

## Build and Development Commands

### Build
```bash
./mvnw clean install
```

### Run Application
```bash
./mvnw spring-boot:run
```

### Run Tests
```bash
./mvnw test
```

### Run Single Test
```bash
./mvnw test -Dtest=ClassName#methodName
```

### Package Application
```bash
./mvnw package
```

## Architecture Notes

This is a new project scaffold. The architecture will be centered around building a high-performance order matching engine for cryptocurrency trading.

**Key Dependencies:**
- MyBatis is configured for database operations (not JPA/Hibernate)
- Actuator endpoints available for monitoring
- Lombok is used for reducing boilerplate code

**Database:**
- MySQL connector configured
- MyBatis mappers should be created in `src/main/resources/mapper/` (typical pattern)
- Configuration in `application.properties`