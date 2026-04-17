package com.teamtobo.tobochatserver.aspects;

import com.teamtobo.tobochatserver.annotations.RequirePermission;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.MemberPermission;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static com.teamtobo.tobochatserver.RolePermission.hasPermission;

@Aspect
@Component
@AllArgsConstructor
public class PermissionAspect {
    private final RoomMemberService roomMemberService;

    @Around("@annotation(com.teamtobo.tobochatserver.annotations.RequireRoomMember)")
    public Object checkMember(ProceedingJoinPoint joinPoint) throws Throwable {

        String userId = getCurrentUserId();
        String roomId = extractRoomId(joinPoint);

        RoomMember member = roomMemberService.getMemberById(userId, roomId);

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  RequirePermission requirePermission) throws Throwable {

        // 1. Lấy userId từ JWT
        String userId = getCurrentUserId();

        // 2. Lấy roomId từ @RoomId
        String roomId = extractRoomId(joinPoint);

        if (roomId == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        // 3. Query member từ DynamoDB
        RoomMember member = roomMemberService.getMemberById(userId, roomId);

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }

        // 4. Check permission
        if (member.getRoomType() == RoomType.GROUP && !hasPermission(member.getRole(), requirePermission.value())) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        // 5. Cho chạy tiếp
        return joinPoint.proceed();
    }

    private String extractRoomId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof RoomId) {
                    return (String) args[i];
                }
            }
        }

        return null;
    }

    private String getCurrentUserId() {
        Object principal = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        throw new RuntimeException("Invalid JWT");
    }
}