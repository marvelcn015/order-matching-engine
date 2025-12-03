package cex.crypto.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 統一的 API 響應包裝類
 * @param <T> 響應數據類型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /**
     * HTTP 狀態碼 (200, 400, 500 等)
     */
    private Integer code;

    /**
     * 響應消息
     */
    private String message;

    /**
     * 響應數據
     */
    private T data;

    /**
     * 時間戳
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    /**
     * 成功響應（帶數據）
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("成功")
                .data(data)
                .build();
    }

    /**
     * 成功響應（帶消息和數據）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 失敗響應（指定狀態碼和消息）
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * 失敗響應（默認 400）
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .code(400)
                .message(message)
                .data(null)
                .build();
    }
}
