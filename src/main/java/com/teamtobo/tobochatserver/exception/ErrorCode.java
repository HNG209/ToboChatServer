package com.teamtobo.tobochatserver.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    UNCATEGORIZED(9999, "[9999] Uncategorized exception", HttpStatus.INTERNAL_SERVER_ERROR),
    USERNAME_EXISTED(1001, "[1001] Username existed", HttpStatus.BAD_REQUEST),
    USERNAME_NOT_FOUND(1002, "[1002] Username not found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(1003, "[1003] User not found", HttpStatus.NOT_FOUND),
    EMAIL_EXISTED(1004, "[1004] Email already exists", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(2001, "[2001] Role existed", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(2002, "[2002] Role not found", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND(2003, "[2003] Permission not found", HttpStatus.NOT_FOUND),
    FIELD_BLANK(2008, "[2008] Field cannot be blank", HttpStatus.BAD_REQUEST),
    EMAIL_INVALID(3000, "[3000] Invalid email", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(3001, "[3001] Invalid password", HttpStatus.UNAUTHORIZED),
    USERNAME_INVALID(3002, "[3002] Invalid username", HttpStatus.BAD_REQUEST),
    KEY_INVALID(3003, "[3003] Invalid enum key", HttpStatus.INTERNAL_SERVER_ERROR),
    OWNERSHIP_INVALID(3004, "[3004] Ownership invalid", HttpStatus.BAD_REQUEST),
    SESSION_EXPIRED(3005, "[3005] Session expired", HttpStatus.UNAUTHORIZED),
    CUSTOMER_NOT_ALLOWED(3006, "[3006] Customer not allowed", HttpStatus.FORBIDDEN),
    CUSTOMER_ONLY(3007, "[3007] Customer only", HttpStatus.FORBIDDEN),
    BODY_NOT_SPECIFIED(3008, "[3008] Body not specified", HttpStatus.BAD_REQUEST),
    PHONE_INVALID(3009, "[3004] Invalid phone number", HttpStatus.BAD_REQUEST),
    PASSWORD_SIZE_INVALID(3010, "[3010] Password must be at least 6 digits", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(4004, "[4004] Unauthenticated", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_MISSING(4005, "[4005] Refresh token missing", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(4003, "[4003] Access denied", HttpStatus.FORBIDDEN),
    //    INVALID_DOB(3003, "[3003] Invalid date of birth", HttpStatus.BAD_REQUEST),
    CUSTOMER_NOT_FOUND(4001, "[4001] Customer not found", HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND(4002, "[4002] Product not found", HttpStatus.NOT_FOUND),
    CART_ITEM_NOT_FOUND(4003, "[4003] Cart item not found", HttpStatus.NOT_FOUND),
    CART_ITEM_INVALID(4004, "[4004] Cart item invalid", HttpStatus.BAD_REQUEST),
    CART_NOT_FOUND(4005, "[4005] Cart not found", HttpStatus.NOT_FOUND),
    CART_EMPTY(4006, "[4006] Cart is empty", HttpStatus.BAD_REQUEST),
    PRODUCT_OUT_OF_STOCK(4007, "[4007] Product out of stock", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(4008, "[4008] Order not found", HttpStatus.NOT_FOUND),
    INVALID_QUANTITY(4009, "[4009] Invalid quantity", HttpStatus.BAD_REQUEST),
    PERMISSION_EXISTED(4010, "[4010] Permission existed", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_FOUND(4011, "[4011] Category not found", HttpStatus.NOT_FOUND),
    ADDRESS_NOT_FOUND(4012, "[4011] Address not found", HttpStatus.NOT_FOUND),

    MANAGER_USERNAME_SIZE_INVALID(40010, "[40011] Username must be at least 6 characters long", HttpStatus.BAD_REQUEST),
    MANAGER_USERNAME_INVALID(40020, "[40020] Username must not be null ", HttpStatus.BAD_REQUEST),
    MANAGER_FULLNAME_SIZE_INVALID(40010, "[40012] Full name must be at least 6 characters long", HttpStatus.BAD_REQUEST),
    MANAGER_FULLNAME_INVALID(40021, "[40021] Full name must be not null", HttpStatus.BAD_REQUEST),
    ROLE_SIZE_INVALID(40011, "[40011] At least one role is required", HttpStatus.BAD_REQUEST),
    ROLE_INVALID(40012, "[40012] Roles cannot be null", HttpStatus.BAD_REQUEST),
    ROLE_NAME_INVALID(40013, "[40013] Role name must follow the format ROLE_ followed by uppercase letters", HttpStatus.BAD_REQUEST),
    ROLE_NAME_SIZE_INVALID(40014, "[40014] Role name cannot be null", HttpStatus.BAD_REQUEST),
    ROLE_ADMIN_NOTUPDATE(40020, "[40020] Role Admin can not update", HttpStatus.BAD_REQUEST),

    PERMISSION_SIZE_INVALID(40015, "[40015] Permission list must contain at least one permission", HttpStatus.BAD_REQUEST),
    PERMISSION_INVALID(40016, "[40016] Permission list cannot be null", HttpStatus.BAD_REQUEST),
    ROLE_DESCRIPTION_SIZE_INVALID(40017, "[40017] Description must be at least 10 characters long", HttpStatus.BAD_REQUEST),
    ROLE_DESCRIPTION_INVALID(40018, "[40018] Description cannot be null", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_INVALID(40019, "[40019] Role name must not be null", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_SIZE_INVALID(40019, "[40019] Role name must contain only uppercase letters and underscores (e.g., ADD_VIEW)", HttpStatus.BAD_REQUEST),
    PERMISSION_IN_USE(40021, "[40021]Cannot delete Permission because it is currently assigned to a Role", HttpStatus.BAD_REQUEST),


    PRODUCT_NAME_INVALID(5001, "[5001] Product name must be at least 3 characters long", HttpStatus.BAD_REQUEST),
    PRODUCT_CATEGORY_INVALID(5002, "[5002] Product's category must be null or empty", HttpStatus.BAD_REQUEST),
    PRODUCT_PRICE_MISSING(5003, "[5003] Product's price cannot be null", HttpStatus.BAD_REQUEST),
    PRODUCT_PRICE_INVALID(5004, "[5004] Product's price must be a positive number", HttpStatus.BAD_REQUEST),
    PRODUCT_UNIT_INVALID(5005, "[5005] Product's unit cannot be null", HttpStatus.BAD_REQUEST),
    PRODUCT_STOCK_MISSING(5006, "[5006] Product's stock cannot be null", HttpStatus.BAD_REQUEST),
    PRODUCT_STOCK_INVALID(5007, "[5007] Product's stock must be a positive number ", HttpStatus.BAD_REQUEST),
    PRODUCT_MEDIA_INVALID(5008, "[5008] Product's media should contain atleast 1 image", HttpStatus.BAD_REQUEST),
    CATEGORY_PRODUCT_EXIST(5009, "[5009] Category is still existing in some products", HttpStatus.BAD_REQUEST),
    CATEGORY_NAME_INVALID(5010, "[5010] Category name cannot be null", HttpStatus.BAD_REQUEST),
    ;


    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}