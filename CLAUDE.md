# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**High-Performance Order Matching Engine** - A production-ready Spring Boot application for cryptocurrency exchange order matching with event-driven architecture, asynchronous order processing, and real-time updates via SSE.

**Tech Stack:**
- Spring Boot 3.5.8
- Java 17
- MyBatis 3.0.5 for data persistence
- MySQL database (H2 for integration tests)
- Apache Kafka for event streaming
- Redis/Redisson for distributed locking and caching
- Maven build system
- Springdoc OpenAPI for API documentation
- Prometheus for metrics collection

**Package Structure:**
```
cex.crypto.trading/
├── config/           # Configuration classes (MyBatis, Kafka, Redis, Swagger)
├── controller/       # REST API controllers + SSE streaming
├── domain/           # Entity models (Order, Trade, OrderBook, User, UserBalance)
├── dto/              # Data Transfer Objects (Request/Response)
├── enums/            # Enumerations (OrderSide, OrderType, OrderStatus)
├── event/            # Kafka event models (OrderCreatedEvent, TradeExecutedEvent, OrderStatusEvent)
├── exception/        # Custom exceptions and global exception handler
├── interceptor/      # Rate limiting interceptor
├── mapper/           # MyBatis mapper interfaces
├── service/
│   ├── kafka/        # Kafka producers and consumers
│   ├── redis/        # Redis-based services (order book sync)
│   ├── cache/        # Caching services (Bloom filter, distributed lock, cache warmer)
│   └── sse/          # SSE emitter registry for real-time updates
└── strategy/         # Order matching strategy implementations
```

## Build and Development Commands

### Build
```bash
./mvnw clean install
```

### Run Application
```bash
# Start dependencies first (Kafka, Redis)
./start-kafka.sh

# Run application
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

### Access Prometheus Metrics
```
http://localhost:8080/actuator/prometheus
```

### Access Kafka UI
```
http://localhost:8090
```

## Architecture

### Event-Driven Order Processing Flow

**Asynchronous Order Submission (202 ACCEPTED Pattern):**

```
1. Client → POST /api/v1/orders → OrderController
2. OrderController → Validate request
3. OrderController → Create Order entity (PENDING status)
4. OrderController → Publish OrderCreatedEvent to Kafka (order-input topic)
5. OrderController → Return 202 ACCEPTED immediately (with orderId and correlationId)
6. Client → Poll GET /api/v1/orders/{orderId} OR connect to SSE /api/v1/stream/order-status

[Async Processing]
7. MatchingEngineConsumer → Consume from order-input topic
8. MatchingEngineConsumer → Execute matching logic (synchronized per symbol)
9. MatchingEngineConsumer → Persist orders, trades, order book (transactional)
10. MatchingEngineConsumer → Publish OrderStatusEvent to order-status-update topic
11. MatchingEngineConsumer → Publish TradeExecutedEvent to trade-output topic
12. OrderStatusConsumer → Consume status updates and broadcast via SSE to connected clients
```

**Key Topics:**
- `order-input`: New orders awaiting matching
- `order-status-update`: Order status changes (for SSE broadcasting)
- `trade-output`: Executed trades
- `order-input-dlq`: Failed orders (Dead Letter Queue)
- `trade-output-dlq`: Failed trade events (Dead Letter Queue)

**Partitioning Strategy:**
- Partition key = symbol (e.g., "BTC-USD")
- Ensures same trading pair goes to same partition → ordering guarantees
- Different symbols can be processed concurrently

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

- **User**: User account entity
  - Fields: userId, username, email, passwordHash, createdAt, updatedAt

- **UserBalance**: Multi-currency balance tracking
  - Fields: id, userId, currency, availableBalance, frozenBalance
  - Supports BTC, USD, ETH, etc.

#### 2. Service Layer (`service/`)

**Core Services:**
- **MatchingEngineService**: DEPRECATED - Old synchronous API (kept for backwards compatibility)
  - Use Kafka-based async flow instead (OrderEventProducerService → MatchingEngineConsumer)

- **OrderService**: Order lifecycle management
  - `createOrder()`: Create order with PENDING status
  - `updateOrder()`: Update order (status, filledQuantity)
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

- **UserService**: User account management
  - CRUD operations for user accounts
  - Password hashing with BCrypt

- **UserBalanceService**: Multi-currency balance management
  - Distributed locking for balance updates (prevents double-spend)
  - Multi-level caching (Caffeine local + Redis distributed)
  - `updateBalance()`: Atomic balance updates with optimistic locking

- **IdempotencyService**: Deduplication for Kafka consumers
  - Prevents duplicate message processing
  - Uses Redis for idempotency tracking (TTL: 24 hours)

**Kafka Services (`service/kafka/`):**
- **OrderEventProducerService**: Publishes OrderCreatedEvent to order-input topic
  - Partitions by symbol for ordering guarantees
  - Records idempotency before publishing
  - Waits for Kafka send confirmation (5s timeout)

- **MatchingEngineConsumer**: Consumes from order-input topic
  - Consumer group: `matching-engine-consumer-group`
  - Concurrency: 6 threads (configured in KafkaConsumerConfig)
  - Manual acknowledgment (commits offset only after successful processing)
  - Idempotency check → Load order → Execute matching → Persist → Publish events → Acknowledge
  - Error handling: Retry 3 times, then DLQ
  - Metrics: messages received/processed/failed, processing time percentiles

- **OrderStatusConsumer**: Consumes from order-status-update topic
  - Broadcasts status updates to SSE clients via SseEmitterRegistry

- **DlqConsumer**: Monitors dead letter queues for failed messages
  - Logs failed messages for manual intervention

**Redis Services (`service/redis/`):**
- **RedisOrderBookService**: Redis-based order book caching
  - Fallback to MySQL if Redis unavailable
  - TTL: 10 minutes

- **OrderBookSyncService**: Scheduled sync from MySQL to Redis
  - Runs every 5 minutes
  - Registers active symbols and keeps Redis synchronized

**Cache Services (`service/cache/`):**
- **BloomFilterService**: Probabilistic membership test
  - User bloom filter (1M expected insertions, 1% FPP)
  - Order bloom filter (10M expected insertions, 1% FPP)
  - Reduces unnecessary database queries

- **DistributedLockService**: Redis-based distributed locking
  - Uses Redisson RLock (wait: 3s, lease: 10s)
  - Prevents race conditions in balance updates

- **CacheWarmerService**: Scheduled cache preloading
  - Warms up top 1000 active users' balances
  - Runs daily to improve cache hit rate

**SSE Services (`service/sse/`):**
- **SseEmitterRegistry**: Manages SSE connections per user
  - Stores Map<userId, List<SseEmitter>>
  - Broadcasts status updates to all connected clients for a user
  - Auto-cleanup on timeout/error/completion

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
  - `POST /api/v1/orders` - Create order (async, returns 202 ACCEPTED)
  - `GET /api/v1/orders/{orderId}` - Query order
  - `DELETE /api/v1/orders/{orderId}?userId={userId}` - Cancel order

- **OrderBookController** (`/api/v1/orderbook`)
  - `GET /api/v1/orderbook/{symbol}?limit={limit}` - Query aggregated depth

- **OrderStatusStreamController** (`/api/v1/stream`)
  - `GET /api/v1/stream/order-status?userId={userId}` - SSE endpoint for real-time updates
  - `GET /api/v1/stream/order-status/stats` - SSE connection statistics

- **UserController** (`/api/v1/users`)
  - `POST /api/v1/users` - Create user
  - `GET /api/v1/users/{userId}` - Get user details

- **UserBalanceController** (`/api/v1/balances`)
  - `GET /api/v1/balances/{userId}/{currency}` - Get balance
  - `POST /api/v1/balances/update` - Update balance (admin operation)

- All responses wrapped in `ApiResponse<T>` with HTTP status codes
- Global exception handling via `GlobalExceptionHandler`
- Swagger documentation with `@Tag` and `@Operation` annotations
- Rate limiting via `RateLimiterInterceptor` (10 req/sec default)

### Concurrency Control

**Three-Level Protection:**

1. **In-Memory Synchronization**
   - `synchronized (orderBook)` during entire match (in MatchingEngineConsumer)
   - Per-symbol locking (different symbols process concurrently via Kafka partitions)

2. **Optimistic Locking**
   - OrderBook.version field incremented on each update
   - `@Retryable(maxAttempts=3)` on version conflicts
   - UserBalance uses optimistic locking for balance updates

3. **Transaction Boundaries**
   - `@Transactional` on `persistMatchResult()` ensures ACID
   - Rollback on any error

4. **Distributed Locking (for balance updates)**
   - Redisson RLock for balance update operations
   - Prevents race conditions across multiple instances

### Order Status Flow

```
PENDING (initial submission, awaiting Kafka processing)
    ↓
OPEN (active in order book, awaiting match)
    ↓ (partial match)
PARTIALLY_FILLED (some quantity filled, LIMIT orders stay in book)
    ↓ (more matches)
FILLED (completely matched)

OPEN/PARTIALLY_FILLED → (user cancels) → CANCELLED
PENDING → (validation fails) → REJECTED
PENDING/OPEN → (processing error) → FAILED

MARKET orders:
- If insufficient liquidity: PARTIALLY_FILLED (not added to book)
- Never stay in order book with OPEN status
```

### Idempotency Handling

**Problem:** Kafka consumers may process same message multiple times (network retries, rebalancing)

**Solution:**
1. Producer records messageId in Redis BEFORE publishing to Kafka
2. Consumer checks `idempotencyService.isMessageProcessed(messageId)` first
3. If duplicate detected, skip processing and acknowledge immediately
4. On success, mark as processed: `idempotencyService.markMessageProcessed(messageId, orderId)`
5. On failure, remove idempotency record to allow retry

**TTL:** 24 hours (configurable)

### Error Handling & Dead Letter Queues

**Retry Policy (MatchingEngineConsumer):**
1. Retry up to 3 times with exponential backoff (100ms, 200ms, 400ms)
2. After 3 failures → send to Dead Letter Queue (order-input-dlq)
3. Update order status to FAILED
4. Publish FAILED status event for SSE broadcasting

**DLQ Monitoring:**
- `DlqConsumer` monitors DLQs and logs failed messages
- Manual intervention required for DLQ messages (operational alerting)

## Database Schema

### Tables

**users**
```sql
user_id         BIGINT PRIMARY KEY AUTO_INCREMENT
username        VARCHAR(50) NOT NULL UNIQUE
email           VARCHAR(100) NOT NULL UNIQUE
password_hash   VARCHAR(255) NOT NULL
created_at      DATETIME(6) NOT NULL
updated_at      DATETIME(6) NOT NULL
```

**user_balances**
```sql
id                  BIGINT PRIMARY KEY AUTO_INCREMENT
user_id             BIGINT NOT NULL (FK → users)
currency            VARCHAR(10) NOT NULL (USD, BTC, ETH, etc.)
available_balance   DECIMAL(20, 8) NOT NULL
frozen_balance      DECIMAL(20, 8) NOT NULL
created_at          DATETIME(6) NOT NULL
updated_at          DATETIME(6) NOT NULL

UNIQUE KEY uk_user_currency (user_id, currency)
```

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

INDEX idx_user_id (user_id)
INDEX idx_symbol_status (symbol, status)
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

INDEX idx_symbol_created (symbol, created_at)
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
- Embedded Redis (via embedded-redis library)
- Embedded Kafka (via spring-kafka-test)
- `@SpringBootTest` with `@ActiveProfiles("test")`
- `BaseIntegrationTest`: Provides assertion utilities and cleanup
- `OrderTestBuilder`: Fluent test data builder

**Core Scenarios (8 tests - all passing):**
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
- Kafka event publishing
- Redis caching
- Distributed locking

### Known Limitations

- **Concurrency tests**: H2 locking differs from MySQL - use TestContainers for true concurrent testing
- **OrderMapTypeHandler**: Use `@JsonIgnore` on computed Order properties to avoid serialization issues
- **Order removal**: Use `removeIf(o -> o.getOrderId().equals(...))` to avoid timestamp equality issues
- **Kafka tests**: Embedded Kafka may have timing issues - use `@DirtiesContext` if needed

## API Documentation

### Swagger UI
Access at: `http://localhost:8080/swagger-ui.html`

### Example Requests

**Create LIMIT Order (Async):**
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "symbol": "BTC-USD",
    "side": "BUY",
    "type": "LIMIT",
    "price": "50000.00",
    "quantity": "1.5"
  }'
```

**Response (202 ACCEPTED):**
```json
{
  "code": 202,
  "message": "Order accepted for processing",
  "data": {
    "orderId": 123,
    "status": "PENDING",
    "message": "Order accepted for processing",
    "correlationId": "abc-123-xyz"
  },
  "timestamp": "2025-12-12T12:00:00"
}
```

**Query Order Status:**
```bash
curl http://localhost:8080/api/v1/orders/123
```

**Connect to SSE for Real-Time Updates:**
```javascript
const eventSource = new EventSource('http://localhost:8080/api/v1/stream/order-status?userId=1');

eventSource.addEventListener('order-status-update', (event) => {
  const orderStatus = JSON.parse(event.data);
  console.log('Order status:', orderStatus);
  // { orderId: 123, status: "FILLED", filledQuantity: 1.5, ... }
});

eventSource.addEventListener('connected', (event) => {
  console.log('Connected to SSE:', event.data);
});

eventSource.onerror = (error) => {
  console.error('SSE error:', error);
};
```

**Query Order Book Depth:**
```bash
curl http://localhost:8080/api/v1/orderbook/BTC-USD?limit=10
```

**Response Format:**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "symbol": "BTC-USD",
    "bids": [
      { "price": "50000.00", "quantity": "2.5" },
      { "price": "49999.00", "quantity": "1.0" }
    ],
    "asks": [
      { "price": "50001.00", "quantity": "3.0" },
      { "price": "50002.00", "quantity": "1.5" }
    ]
  },
  "timestamp": "2025-12-12T12:00:00"
}
```

## Development Guidelines

### Code Comments
- **IMPORTANT**: All code comments MUST be in English
- Use JavaDoc for public APIs
- Inline comments for complex logic only

### Adding New Features

1. **New Order Types**: Implement `OrderMatchingStrategy` interface
2. **New API Endpoints**: Add controller methods with validation + Swagger annotations
3. **Database Changes**: Update schema.sql, MyBatis mappers, and test schema
4. **Kafka Events**: Define event class in `event/` package, add producer/consumer
5. **Tests**: Add integration tests in `MatchingEngineIntegrationTest`

### Common Pitfalls

1. **JSON Serialization**: Mark computed properties with `@JsonIgnore`
2. **Order Removal**: Use `orderId` comparison, not object equality
3. **Timestamps**: Use `LocalDateTime.now()` for consistency
4. **BigDecimal**: Always use for prices/quantities (avoid doubles)
5. **Optimistic Locking**: Ensure `@Retryable` is enabled
6. **Kafka Partitioning**: Always use symbol as partition key for ordering guarantees
7. **Idempotency**: Always check `isMessageProcessed()` before processing Kafka messages
8. **SSE Cleanup**: Always remove emitters on completion/timeout/error to prevent memory leaks
9. **Distributed Locks**: Always use try-finally to release locks, even on exceptions
10. **Cache Invalidation**: Invalidate Redis cache when updating MySQL (write-through pattern)

## Key Dependencies

**Core:**
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

**Event Streaming:**
- **spring-kafka**: Kafka integration
- **spring-kafka-test**: Embedded Kafka for tests

**Caching & Locking:**
- **spring-boot-starter-data-redis**: Redis integration
- **redisson-spring-boot-starter**: Distributed locks
- **caffeine**: Local in-memory caching
- **spring-boot-starter-cache**: Spring Cache abstraction
- **embedded-redis**: Embedded Redis for tests

**Metrics:**
- **micrometer-registry-prometheus**: Prometheus metrics export

**Security:**
- **spring-security-crypto**: Password hashing (BCrypt)

## Configuration

### Production (`application.properties`)
```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/trading
spring.datasource.username=root
spring.datasource.password=springboot

# MyBatis
mybatis.mapper-locations=classpath:mapper/*.xml

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Actuator (Prometheus)
management.endpoints.web.exposure.include=health,info,prometheus,metrics
```

### Kafka (`application-kafka.properties`)
```properties
# Bootstrap
spring.kafka.bootstrap-servers=localhost:9092

# Topics
kafka.topics.order-input=order-input
kafka.topics.trade-output=trade-output
kafka.topics.order-status-update=order-status-update
kafka.topics.order-input-dlq=order-input-dlq

# Producer
spring.kafka.producer.acks=1
spring.kafka.producer.enable-idempotence=false
spring.kafka.producer.compression-type=snappy

# Consumer
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.consumer.max-poll-records=100
```

### Test (`application-test.properties`)
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.sql.init.schema-locations=classpath:schema-test.sql
```

## Infrastructure Setup

### Start Kafka Infrastructure
```bash
./start-kafka.sh
```

This script starts:
- Zookeeper (localhost:2181)
- Kafka Broker (localhost:9092)
- Kafka UI (http://localhost:8090)

**Topics are auto-created on first use**

### Stop Kafka
```bash
docker-compose down
```

### Reset Kafka Data
```bash
docker-compose down -v
```

## Metrics & Monitoring

### Prometheus Metrics Endpoint
```
http://localhost:8080/actuator/prometheus
```

### Custom Metrics

**Kafka Consumer Metrics:**
- `kafka.consumer.messages.received` - Total messages received
- `kafka.consumer.messages.processed` - Total messages successfully processed
- `kafka.consumer.messages.failed` - Total messages failed after retries
- `kafka.consumer.messages.duplicate` - Total duplicate messages detected
- `kafka.consumer.processing.time` - Message processing time (p50, p95, p99)

**Application Metrics:**
- HTTP request metrics (duration, count, status)
- JVM metrics (memory, threads, GC)
- Spring Boot Actuator health checks

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

## Architecture Decision Records (ADRs)

### Why Event-Driven Architecture?

**Problem:** Synchronous order matching blocks HTTP threads, limiting throughput

**Solution:** Async order submission with Kafka-based matching
- HTTP thread returns 202 immediately after Kafka publish
- Matching happens asynchronously in background consumers
- Clients poll or use SSE for status updates

**Trade-offs:**
- Higher throughput (HTTP threads don't wait for matching)
- Better fault tolerance (Kafka replication, DLQ)
- Horizontal scalability (add more consumer instances)
- Increased complexity (eventual consistency, idempotency handling)
- Requires Kafka infrastructure

### Why SSE Instead of WebSocket?

**Rationale:**
- Server-to-client push only (no client-to-server messages needed)
- Simpler protocol (HTTP-based, works with standard browsers)
- Auto-reconnect built into EventSource API
- No need for WebSocket handshake overhead

**Use Case:**
- Order status updates are unidirectional (server pushes to client)
- Client submits orders via REST API (not WebSocket)

### Why Distributed Locks for Balance Updates?

**Problem:** Multiple instances updating same user balance → race conditions

**Solution:** Redisson distributed locks (Redis-based)
- Lock key: `balance:lock:{userId}:{currency}`
- Ensures only one instance can update balance at a time
- Prevents double-spend attacks

**Alternative Considered:** Database-level locking (SELECT FOR UPDATE)
- Doesn't work across multiple database connections
- Increases database contention
- Distributed locks work across multiple instances

## Next Steps

Potential enhancements:
1. Event-driven architecture with Kafka (COMPLETED)
2. Real-time updates via SSE (COMPLETED)
3. User management and balances (COMPLETED)
4. Redis caching and distributed locking (COMPLETED)
5. Prometheus metrics (COMPLETED)
6. Add WebSocket for bidirectional communication (alternative to SSE)
7. Implement order book snapshots for faster recovery
8. Add performance benchmarks (orders/second)
9. Implement market data APIs (OHLCV, ticker, 24h stats)
10. Add JWT-based authentication and authorization
11. Implement circuit breakers (Resilience4j)
12. Add comprehensive logging with structured logs (ELK stack)
13. Implement position management and risk controls
14. Add matching engine performance optimization (order book snapshots in Redis)
- no emoji is allowed in this project