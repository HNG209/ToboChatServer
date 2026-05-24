package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.annotations.RequireRoomMember;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.documents.AttachmentItem;
import com.teamtobo.tobochatserver.entities.enums.AttachmentType;
import com.teamtobo.tobochatserver.services.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Room Attachment Controller", description = "APIs quản lý kho lưu trữ tệp tin trong phòng chat")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class RoomAttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "Lấy danh sách media hoặc file của phòng chat")
    @GetMapping("/rooms/{roomId}/attachments")
    @RequireRoomMember
    public ApiResponse<PageResponse<AttachmentItem>> getRoomAttachments(
            @RoomId @PathVariable String roomId,
            @Parameter @RequestParam AttachmentType type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return ApiResponse.<PageResponse<AttachmentItem>>builder()
                .result(attachmentService.getRoomAttachments(roomId, type.name(), limit, cursor))
                .build();
    }
}