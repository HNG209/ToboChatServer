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
    UPLOAD_ERROR(4099, "[4099] Error uploading file", HttpStatus.INTERNAL_SERVER_ERROR),
    FRIEND_REQUEST_ALREADY_SENT(5001, "[5001] Friend request already sent", HttpStatus.BAD_REQUEST),
    ALREADY_FRIENDS(5002, "[5002] Already friends", HttpStatus.BAD_REQUEST),
    CANNOT_ADD_SELF(5003, "[5003] Cannot send friend request to yourself", HttpStatus.BAD_REQUEST),
    FRIEND_REQUEST_NOT_FOUND(5004, "[5004] Friend request not found", HttpStatus.NOT_FOUND),
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

    ROOM_INVALID(411111, "[411111] Room invalid", HttpStatus.BAD_REQUEST),
    GROUP_SIZE_INVALID(422222, "[42222] Invalid group size", HttpStatus.BAD_REQUEST),
    ROOM_CREATE_ERROR(433333, "[43333] Room create error", HttpStatus.INTERNAL_SERVER_ERROR),
    ROOM_NAME_REQUIRED(444444, "[44444] Room name required", HttpStatus.BAD_REQUEST),
    ROOM_DISBAND_ERROR(455555, "[455555] Room disband error", HttpStatus.BAD_REQUEST),
    CANNOT_LEAVE_ROOM(666777, "[666777] Cannot leave room", HttpStatus.BAD_REQUEST),
    REQUIRE_EXCHANGER(777888, "[777888] Leave room require exchanger", HttpStatus.BAD_REQUEST),

    INVALID_AVATAR_URL(6001, "[6001] Invalid avatar url", HttpStatus.BAD_REQUEST),
    ROOM_NOT_FOUND(40031, "[40031] Room not found", HttpStatus.NOT_FOUND),
    MESSAGE_NOT_FOUND(40041, "[40041] Message not found", HttpStatus.NOT_FOUND),
    NOT_IN_ROOM(50501, "[50501] Not in room", HttpStatus.FORBIDDEN),
    INVALID_PERMISSION(666, "[666] Invalid permission", HttpStatus.BAD_REQUEST),
    ADD_MEMBER_NOT_ALLOWED(7777, "[7777] Add member not allowed", HttpStatus.FORBIDDEN),
    SEND_MESSAGE_NOT_ALLOWED(8888, "[8888] Send message not allowed", HttpStatus.FORBIDDEN),
    ONLY_FRIEND_CAN_ADD(6767, "[6767] Only friends can add to group", HttpStatus.BAD_REQUEST),
    REQUEST_NOT_FOUND(50001, "[50001] Request not found", HttpStatus.NOT_FOUND),
    GROUP_INVITE_HANDLE_FAILED(50002, "[50002] Handle group invite failed", HttpStatus.INTERNAL_SERVER_ERROR),
    PENDING_REQUEST_NOT_FOUND(50003, "[50003] Pending request not found", HttpStatus.NOT_FOUND),
    ROOM_NOT_REQUIRE_APPROVAL(50004, "[50004] Room does not require approval", HttpStatus.BAD_REQUEST),
    GROUP_NOT_REQUIRE_APPROVAL(50005, "[50005] Group does not require approval", HttpStatus.BAD_REQUEST),
    PENDING_NOT_FOUND(50006, "[50006] Pending request not found", HttpStatus.NOT_FOUND),
    CANNOT_DISABLE_APPROVAL_WHEN_PENDING(50007,"[50007] Cannot disable approval when pending requests exist", HttpStatus.BAD_REQUEST),

    ;


    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}