package cex.crypto.trading.exception;

import cex.crypto.trading.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局異常處理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 處理訂單不存在異常
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotFound(OrderNotFoundException e) {
        log.warn("訂單不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
    }

    /**
     * 處理無效訂單異常
     */
    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOrder(InvalidOrderException e) {
        log.warn("無效訂單: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    /**
     * 處理訂單取消異常
     */
    @ExceptionHandler(OrderCancellationException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderCancellation(OrderCancellationException e) {
        log.warn("訂單取消失敗: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    /**
     * 處理訂單簿不存在異常
     */
    @ExceptionHandler(OrderBookNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderBookNotFound(OrderBookNotFoundException e) {
        log.warn("訂單簿不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
    }

    /**
     * 處理參數驗證異常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("請求參數驗證失敗: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .code(400)
                        .message("請求參數驗證失敗")
                        .data(errors)
                        .timestamp(System.currentTimeMillis())
                        .build());
    }

    /**
     * 處理通用業務異常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.error("業務異常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    /**
     * Handle static resource not found (e.g., favicon.ico)
     * Do not log as error since this is expected behavior
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        // Do not log - this is expected for missing static resources like favicon
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Resource not found"));
    }

    /**
     * 處理所有未捕獲的異常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("系統內部錯誤: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "系統內部錯誤，請稍後重試"));
    }
}
