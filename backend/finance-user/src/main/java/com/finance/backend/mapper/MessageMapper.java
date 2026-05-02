package com.finance.backend.mapper;

import com.finance.backend.dto.MessageResponse;
import com.finance.backend.model.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    MessageResponse toResponse(Message entity);
}
