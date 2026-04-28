package com.teamtobo.tobochatserver.aspects;

import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomDomainService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
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

@Aspect
@Component
@AllArgsConstructor
public class RoomStatusAspect {
    private final RoomService roomService;

    @Around("@annotation(com.teamtobo.tobochatserver.annotations.RequireGroup)")
    public Object checkGroup (ProceedingJoinPoint joinPoint) throws Throwable {
        String roomId = extractRoomId(joinPoint);

        Room room = roomService.getRoomById(roomId, true);

        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

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
