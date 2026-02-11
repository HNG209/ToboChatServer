package iuh.fit.se.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.dtos.response.ApiResponse;
import iuh.fit.se.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

// Bắt lỗi ở security filter chain
public class AuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {

        ErrorCode code;

        if (authException instanceof CredentialsExpiredException) {
            code = ErrorCode.SESSION_EXPIRED;
        } else if (authException instanceof BadCredentialsException) {
            code = ErrorCode.UNAUTHENTICATED;
        } else {
            code = ErrorCode.UNAUTHORIZED;
        }

        response.setStatus(code.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> r = ApiResponse.builder()
                .code(code.getCode())
                .message(authException.getMessage())
                .build();

        new ObjectMapper().writeValue(response.getWriter(), r);
    }

}
