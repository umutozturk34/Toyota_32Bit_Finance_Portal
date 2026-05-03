package com.finance.backend.dto.response;

import java.util.List;

public record TaskStatusResponse(
        List<TaskInfoResponse> running,
        List<TaskInfoResponse> history,
        int runningCount) {
}
