package com.example.wallet.dto;

import lombok.Data;

@Data
public class WebhookPayload {
    private String event;
    private String accountId;
    private String amount;
}
