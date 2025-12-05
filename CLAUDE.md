# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**High-Performance Order Matching Engine** - A production-ready Spring Boot application for cryptocurrency exchange order matching with thread-safe concurrent order processing, price-time priority matching, and comprehensive REST APIs.

**Tech Stack:**
- Spring Boot 3.5.8
- Java 17
- MyBatis 3.0.5 for data persistence
- MySQL database (H2 for integration tests)
- Maven build system
- Jackson for JSON serialization
- Springdoc OpenAPI for API documentation

**Package Structure:**
```
cex.crypto.trading/
├── config/           # Configuration classes (MyBatis, Matching Strategies, Swagger)
├── controller/       # REST API controllers
├── domain/           # Entity models (Order, Trade, OrderBook)
├── dto/              # Data Transfer Objects (Request/Response)
├── enums/            # Enumerations (OrderSide, OrderType, OrderStatus)
├── exception/        # Custom exceptions and global exception handler
├── mapper/           # MyBatis mapper interfaces
├── service/          # Business logic layer
└── strategy/         # Order matching strategy implementations
```

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

### Run Integration Tests Only
```bash
./mvnw test -Dtest=MatchingEngineIntegrationTest
```

### Run Single Test
```bash
./mvnw test -Dtest=ClassName#methodName
```

### Package Application
```bash
./mvnw package
```

### Access Swagger UI
```
http://localhost:8080/swagger-ui.html
```

## Architecture

### Core Components

#### 1. Domain Model (`domain/`)
- **Order**: Order entity with thread-safe fields, optimistic locking support
  - Fields: orderId, userId, symbol, side, type, price, quantity, filledQuantity, status
  - Computed properties: `getRemainingQuantity()`, `isFilled()`, `isPartiallyFilled()`
  - `@JsonIgnore` on computed properties to avoid serialization issues

- **Trade**: Trade execution record
  - Fields: tradeId, buyOrderId, sellOrderId, symbol, price, quantity, createdAt
  - Immutable after creation

- **OrderBook**: Thread-safe order book using ConcurrentSkipListMap
  - `buyOrders`: Descending order (highest price first)
  - `sellOrders`: Ascending order (lowest price first)
  - Version field for optimistic locking

#### 2. Service Layer (`service/`)
- **MatchingEngineService**: Core orchestration with `@Transactional` boundaries
  - `processOrder()`: Main entry point with synchronized OrderBook access
  - Delegates to matching strategies based on OrderType
  - Atomic persistence of orders, trades, and order book state

- **OrderService**: Order lifecycle management
  - CRUD operations with status transitions
  - `cancelOrder()`: Remove from book and update status (with authorization)
  - `validateCreateOrderRequest()`: Business rule validation

- **OrderBookService**: Order book state management
  - `getOrCreateOrderBook()`: Lazy initialization
  - `addOrderToBook()`: Add orders to price level (FIFO)
  - `removeOrderFromBook()`: Remove by orderId to avoid equality issues
  - `saveOrderBook()`: Persist with optimistic locking + `@Retryable`
  - `getOrderBookDepth()`: Aggregate order book depth with limit

- **TradeService**: Trade recording and queries
  - `createTrade()`: Persist executed trades
  - Query by orderId or symbol with time range

#### 3. Strategy Pattern (`strategy/`)
- **OrderMatchingStrategy**: Interface for matching algorithms
  - `match(Order incomingOrder, OrderBook orderBook): MatchResult`

- **LimitOrderMatchingStrategy**: Price-time priority matching
  - BUY orders match against sell orders where `sellPrice <= buyPrice`
  - SELL orders match against buy orders where `buyPrice >= sellPrice`
  - Partial fills stay in order book
  - FIFO within same price level

- **MarketOrderMatchingStrategy**: Immediate execution
  - No price constraint - crosses multiple price levels
  - Partial fills do NOT stay in order book
  - Status: FILLED, PARTIALLY_FILLED, or REJECTED

#### 4. REST API Layer (`controller/`)
- **OrderController** (`/api/v1/orders`)
  - `POST /api/v1/orders` - Create order (with validation)
  - `GET /api/v1/orders/{orderId}` - Query order
  - `DELETE /api/v1/orders/{orderId}?userId={userId}` - Cancel order

- **OrderBookController** (`/api/v1/orderbook`)
  - `GET /api/v1/orderbook/{symbol}?limit={limit}` - Query aggregated depth

- All responses wrapped in `ApiResponse<T>` with HTTP status codes
- Global exception handling via `GlobalExceptionHandler`
- Swagger documentation with `@Tag` and `@Operation` annotations

### Concurrency Control

**Three-Level Protection:**

1. **In-Memory Synchronization**
   - `synchronized (orderBook)` during entire match
   - Per-symbol locking (different symbols process concurrently)

2. **Optimistic Locking**
   - OrderBook.version field incremented on each update
   - `@Retryable(maxAttempts=3)` on version conflicts

3. **Transaction Boundaries**
   - `@Transactional` on `processOrder()` ensures ACID
   - Rollback on any error

### Order Status Flow

```
PENDING (initial submission)
    ↓
OPEN (active in order book, awaiting match)
    ↓ (partial match)
PARTIALLY_FILLED (some quantity filled, LIMIT orders stay in book)
    ↓ (more matches)
FILLED (completely matched)

OPEN/PARTIALLY_FILLED → (user cancels) → CANCELLED
PENDING → (validation fails) → REJECTED

MARKET orders:
- If insufficient liquidity: PARTIALLY_FILLED (not added to book)
- Never stay in order book with OPEN status
```

## Database Schema

### Tables

**orders**
```sql
order_id         BIGINT PRIMARY KEY AUTO_INCREMENT
user_id          BIGINT NOT NULL
symbol           VARCHAR(20) NOT NULL
side             VARCHAR(10) NOT NULL (BUY/SELL)
type             VARCHAR(10) NOT NULL (LIMIT/MARKET)
price            DECIMAL(20, 8)
quantity         DECIMAL(20, 8) NOT NULL
filled_quantity  DECIMAL(20, 8) NOT NULL DEFAULT 0
status           VARCHAR(20) NOT NULL
created_at       DATETIME(6) NOT NULL
updated_at       DATETIME(6) NOT NULL
```

**trades**
```sql
trade_id        BIGINT PRIMARY KEY AUTO_INCREMENT
buy_order_id    BIGINT NOT NULL (FK → orders)
sell_order_id   BIGINT NOT NULL (FK → orders)
symbol          VARCHAR(20) NOT NULL
price           DECIMAL(20, 8) NOT NULL
quantity        DECIMAL(20, 8) NOT NULL
created_at      DATETIME(6) NOT NULL
```

**order_books**
```sql
id             BIGINT PRIMARY KEY AUTO_INCREMENT
symbol         VARCHAR(20) NOT NULL UNIQUE
buy_orders     JSON NOT NULL (serialized ConcurrentSkipListMap)
sell_orders    JSON NOT NULL (serialized ConcurrentSkipListMap)
version        INT NOT NULL DEFAULT 0
updated_at     DATETIME(6) NOT NULL
```

### MyBatis Type Handlers

- **OrderSideTypeHandler**: Enum ↔ VARCHAR
- **OrderTypeTypeHandler**: Enum ↔ VARCHAR
- **OrderStatusTypeHandler**: Enum ↔ VARCHAR
- **OrderMapTypeHandler**: ConcurrentSkipListMap ↔ JSON/CLOB
  - Handles complex nested structures with Jackson
  - Deserializes with proper comparators (buy: descending, sell: ascending)

## Testing Strategy

### Integration Tests (`src/test/java`)

**Test Infrastructure:**
- H2 in-memory database (MySQL compatibility mode)
- `@SpringBootTest` with `@ActiveProfiles("test")`
- `BaseIntegrationTest`: Provides assertion utilities and cleanup
- `OrderTestBuilder`: Fluent test data builder

**Core Scenarios (8 tests - all passing ✅):**
1. LIMIT order exact match - both orders fully filled
2. LIMIT order partial fill - incoming order partially filled
3. LIMIT order crosses multiple price levels
4. LIMIT order no match - added to order book
5. LIMIT order price-time priority (FIFO)
6. MARKET order complete fill across price levels
7. MARKET order insufficient liquidity - partial fill
8. Order cancellation - remove from book

**Test Execution:**
```bash
./mvnw test -Dtest=MatchingEngineIntegrationTest
```

**Coverage:**
- Order creation and persistence
- LIMIT/MARKET order matching
- Price-time priority
- Order book state management
- Trade generation
- Order cancellation
- Database persistence validation

### Known Limitations

- **Concurrency tests**: H2 locking differs from MySQL - use TestContainers for true concurrent testing
- **OrderMapTypeHandler**: Use `@JsonIgnore` on computed Order properties to avoid serialization issues
- **Order removal**: Use `removeIf(o -> o.getOrderId().equals(...))` to avoid timestamp equality issues

## API Documentation

### Swagger UI
Access at: `http://localhost:8080/swagger-ui.html`

### Example Requests

**Create LIMIT Order:**
```json
POST /api/v1/orders
{
  "userId": 1,
  "symbol": "BTC-USD",
  "side": "BUY",
  "type": "LIMIT",
  "price": "50000.00",
  "quantity": "1.5"
}
```

**Create MARKET Order:**
```json
POST /api/v1/orders
{
  "userId": 2,
  "symbol": "BTC-USD",
  "side": "SELL",
  "type": "MARKET",
  "quantity": "0.5"
}
```

**Query Order Book Depth:**
```
GET /api/v1/orderbook/BTC-USD?limit=10
```

**Response Format:**
```json
{
  "code": 200,
  "message": "Success",
  "data": { ... },
  "timestamp": "2025-12-04T12:00:00"
}
```

## Development Guidelines

### Code Comments
- **IMPORTANT**: All code comments MUST be in English
- Use JavaDoc for public APIs
- Inline comments for complex logic only

### Adding New Features

1. **New Order Types**: Implement `OrderMatchingStrategy` interface
2. **New API Endpoints**: Add controller methods with validation
3. **Database Changes**: Update schema.sql and MyBatis mappers
4. **Tests**: Add integration tests in `MatchingEngineIntegrationTest`

### Common Pitfalls

1. **JSON Serialization**: Mark computed properties with `@JsonIgnore`
2. **Order Removal**: Use `orderId` comparison, not object equality
3. **Timestamps**: Use `LocalDateTime.now()` for consistency
4. **BigDecimal**: Always use for prices/quantities (avoid doubles)
5. **Optimistic Locking**: Ensure `@Retryable` is enabled

## Key Dependencies

- **spring-boot-starter-web**: REST API
- **spring-boot-starter-actuator**: Monitoring endpoints
- **mybatis-spring-boot-starter**: MyBatis integration
- **mysql-connector-j**: MySQL database driver
- **h2**: In-memory database for tests
- **spring-retry**: Optimistic locking retry
- **spring-aspects**: AOP for @Retryable
- **spring-boot-starter-validation**: JSR-303 validation
- **springdoc-openapi-starter-webmvc-ui**: Swagger documentation
- **lombok**: Reduce boilerplate code

## Configuration

### Production (`application.properties`)
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/trading
spring.datasource.username=root
spring.datasource.password=your_password
mybatis.mapper-locations=classpath:mapper/*.xml
```

### Test (`application-test.properties`)
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.sql.init.schema-locations=classpath:schema-test.sql
```

## Next Steps

Potential enhancements:
1. Add WebSocket for real-time order book updates
2. Implement order book snapshots for persistence
3. Add performance benchmarks (orders/second)
4. Implement market data APIs (OHLCV, ticker)
5. Add user authentication and authorization
6. Implement rate limiting and circuit breakers
7. Add comprehensive logging and metrics