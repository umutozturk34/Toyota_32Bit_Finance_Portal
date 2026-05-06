package com.finance.notification.messaging.service;

import com.finance.notification.messaging.dto.MessageResponse;
import com.finance.notification.messaging.model.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    MessageResponse toResponse(Message entity);
}
