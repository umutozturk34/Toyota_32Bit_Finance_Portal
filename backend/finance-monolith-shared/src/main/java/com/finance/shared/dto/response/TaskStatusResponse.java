package com.finance.shared.dto.response;

import java.util.List;

public record TaskStatusResponse(
        List<TaskInfoResponse> running,
        List<TaskInfoResponse> history,
        int runningCount) {
}
