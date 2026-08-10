package com.trade.market.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "broker-token-mode-client", url = "${broker.service.url}")
public interface BrokerTokenModeClient {

    @GetMapping("/api/token/mode")
    BrokerTokenModeResponse getTokenMode(@RequestParam(name = "clientId", required = false) String clientId,
                                        @RequestParam(name = "appName", required = false) String appName);
}
