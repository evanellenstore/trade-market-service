package com.trade.market.controller;

import com.trade.market.dto.IndicatorBackfillRequest;
import com.trade.market.service.IndicatorBackfillService;
import com.trade.market.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market/indicator")
@RequiredArgsConstructor
public class IndicatorBackfillController {
    private final IndicatorBackfillService indicatorBackfillService;

    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<String>> backfill(@Valid @RequestBody IndicatorBackfillRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                indicatorBackfillService.start(request), "Indicator backfill started"));
    }

    @PostMapping("/backfill/all")
    public ResponseEntity<ApiResponse<String>> backfillAll() {
        return ResponseEntity.ok(ApiResponse.success(
                indicatorBackfillService.startAll(), "Indicator backfill started"));
    }
}