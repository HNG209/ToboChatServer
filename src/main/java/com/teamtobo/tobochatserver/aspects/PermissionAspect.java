package com.teamtobo.tobochatserver.aspects;

import com.teamtobo.tobochatserver.annotations.RequireMemberPermission;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.dtos.response.MemberPermissionsResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomMemberResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.MemberPermission;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
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
public class PermissionAspect {
    private final RoomMemberService roomMemberService;
    private final RoomService roomService;

    @Around("@annotation(com.teamtobo.tobochatserver.annotations.RequireRoomMember)")
    public Object checkMember(ProceedingJoinPoint joinPoint) throws Throwable {

        String userId = getCurrentUserId();
        String roomId = extractRoomId(joinPoint); // group id: uuid, DM id: id1_id2

        // nếu định dạng là DM id thì proceed tiếp
        assert roomId != null;
        if (roomId.contains("_")) {
            String[] parts = roomId.split("_");

            if (parts.length != 2) {
                throw new AppException(ErrorCode.ROOM_INVALID);
            }

            // user phải là 1 trong 2 người
            if (!userId.equals(parts[0]) && !userId.equals(parts[1])) {
                throw new AppException(ErrorCode.NOT_IN_ROOM);
            }

            // hợp lệ → cho đi tiếp luôn (KHÔNG cần query DB)
            return joinPoint.proceed();
        }

        RoomMember member = roomMemberService.getMemberById(userId, roomId);

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(com.teamtobo.tobochatserver.annotations.RequireAdmin)")
    public Object checkAdmin(ProceedingJoinPoint joinPoint) throws Throwable {
        String userId = getCurrentUserId();
        String roomId = extractRoomId(joinPoint);

        RoomMember member = roomMemberService.getMemberById(userId, roomId);

        if(member == null)
            throw new AppException(ErrorCode.NOT_IN_ROOM);

        if(member.getRole() != MemberRole.ADMIN)
            throw new AppException(ErrorCode.INVALID_PERMISSION);

        return joinPoint.proceed();
    }

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  RequireMemberPermission requirePermission) throws Throwable {

        // Lấy userId từ JWT
        String userId = getCurrentUserId();

        // Lấy roomId từ @RoomId
        String roomId = extractRoomId(joinPoint);

        Room room = roomService.getRoomById(roomId, false);

        RoomMemberResponse member = roomMemberService.getMember(userId, roomId);

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }

        MemberPermissionsResponse permissionsResponse = roomMemberService.buildMemberPermission(member, room);

        MemberPermission requiredPermission = requirePermission.value();
        boolean hasPermission = false;

        switch (requiredPermission) {
            case ADD_MEMBER -> hasPermission = permissionsResponse.isCanAddMember();
            case SEND_MESSAGE -> hasPermission = permissionsResponse.isCanSendMessage();
            case UPDATE_ROOM_METADATA -> hasPermission = permissionsResponse.isCanUpdateMetadata();
            case UPDATE_ROOM_SETTINGS -> hasPermission = permissionsResponse.isCanUpdateRoomSettings();
            case APPROVE_MEMBER -> hasPermission = permissionsResponse.isCanApproveMember();
            case DISBAND_GROUP -> hasPermission = permissionsResponse.isCanDisbandGroup();
            case REMOVE_MEMBER -> hasPermission = permissionsResponse.isCanRemoveMember();
            case GET_PENDING_REQUESTS -> hasPermission = permissionsResponse.isCanGetPendingRequests();
            case UPDATE_MEMBER_ROLE -> hasPermission = permissionsResponse.isCanUpdateMemberRole();
            default -> {}
        }

        if (!hasPermission)
            throw new AppException(ErrorCode.INVALID_PERMISSION);

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