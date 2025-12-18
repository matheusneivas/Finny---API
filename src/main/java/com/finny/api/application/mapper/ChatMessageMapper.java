package com.finny.api.application.mapper;

import com.finny.api.application.dto.response.ChatHistoryResponse;
import com.finny.api.domain.entity.ChatMessage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {
    ChatHistoryResponse toResponse(ChatMessage chatMessage);
}
