package com.finance.shared.dto.response;

import java.util.List;

/** API snapshot of all tracked tasks: currently running, recent history, and the running count. */
public record TaskStatusResponse(
        List<TaskInfoResponse> running,
        List<TaskInfoResponse> history,
        int runningCount) {
}
