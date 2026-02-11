package iuh.fit.se.exception;

import iuh.fit.se.dtos.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler { // Xử lý lỗi tập trung

    // Bắt lỗi đã được định nghĩa (AppException)
    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException e) {
        ApiResponse<Void> response = new ApiResponse<>();

        response.setCode(e.getErrorCode().getCode());
        response.setMessage(e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getStatusCode())
                .body(response);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        ApiResponse<Void> response = new ApiResponse<>();

        String key = exception.getFieldError().getDefaultMessage();
        ErrorCode code = ErrorCode.KEY_INVALID;

        try {
            code = ErrorCode.valueOf(key);
        } catch (Exception e){}

        response.setCode(code.getCode());
        response.setMessage(code.getMessage());

        return ResponseEntity
                .status(code.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(value = AuthorizationDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthorizationDeniedException(AuthorizationDeniedException exception) {
        ApiResponse<Void> response = new ApiResponse<>();
        ErrorCode code = ErrorCode.UNAUTHORIZED;

        response.setCode(code.getCode());
        response.setMessage(code.getMessage());

        return ResponseEntity
                .status(code.getStatusCode())
                .body(response);


    }

    @ExceptionHandler(value = RuntimeException.class)
    ResponseEntity<ApiResponse<Void>> handleUncategorizedException(RuntimeException exception){//other exceptions
        ApiResponse<Void> response = new ApiResponse<>();
        ErrorCode code = ErrorCode.UNCATEGORIZED;

        response.setCode(code.getCode());
        response.setMessage(code.getMessage() + " - Type:" + exception);

        return ResponseEntity
                .status(code.getStatusCode())
                .body(response);
    }
}
