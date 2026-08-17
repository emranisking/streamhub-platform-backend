package com.streamhub.platform.subscription.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscribeRequest {
    /** Defaults to "basic" when omitted. */
    private String plan = "basic";
}
