package com.finance.user.client.dto;
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

import java.time.Instant;

public record TokenSnapshot(String token, Instant expiresAt) {

    public boolean isValid(long safetyMarginSeconds) {
        return Instant.now().isBefore(expiresAt.minusSeconds(safetyMarginSeconds));
    }
}
