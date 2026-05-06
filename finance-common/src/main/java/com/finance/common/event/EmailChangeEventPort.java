package com.finance.common.event;

public interface EmailChangeEventPort {

    void publishEmailChangeCode(EmailChangeCodeRequestedEvent event);
}
