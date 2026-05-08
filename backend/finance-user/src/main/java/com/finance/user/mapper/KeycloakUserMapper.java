package com.finance.user.mapper;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

import com.finance.user.dto.AdminUserResponse;
import com.finance.user.dto.KeycloakUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = {Instant.class})
public interface KeycloakUserMapper {

    @Mapping(target = "enabled", expression = "java(source.enabled() != null && source.enabled())")
    @Mapping(target = "createdAt", expression = "java(source.createdTimestamp() != null ? Instant.ofEpochMilli(source.createdTimestamp()) : null)")
    AdminUserResponse toResponse(KeycloakUser source);
}
