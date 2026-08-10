package com.trade.market.service;

import com.trade.market.client.BrokerTokenModeClient;
import com.trade.market.client.BrokerTokenModeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrokerTokenModeService {

    private final BrokerTokenModeClient brokerTokenModeClient;

    public boolean isLiveMode() {
        return isLiveMode(null, null);
    }

    public boolean isLiveMode(String clientId, String appName) {
        BrokerTokenModeResponse response = brokerTokenModeClient.getTokenMode(clientId, appName);
        return response != null && "live".equalsIgnoreCase(response.getMode());
    }
}
