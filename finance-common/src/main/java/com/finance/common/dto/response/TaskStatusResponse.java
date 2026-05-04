package com.finance.common.dto.response;

import java.util.List;

public record TaskStatusResponse(
        List<TaskInfoResponse> running,
        List<TaskInfoResponse> history,
        int runningCount) {
}
