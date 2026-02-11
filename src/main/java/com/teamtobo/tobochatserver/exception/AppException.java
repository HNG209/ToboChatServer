package iuh.fit.se.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppException extends RuntimeException {
    private ErrorCode errorCode;

    public AppException(ErrorCode code) {
        super(code.getMessage());
        this.errorCode = code;
    }
}
