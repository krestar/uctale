package com.uctale.uctale.application.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingProviderCallEventSink implements ProviderCallEventSink {

    @Override
    public void record(ProviderCallEvent event) {
        log.info(
                "provider_call provider={} operation={} sessionId={} turn={} requestId={} idempotencyKey={} latencyMs={} outcome={} retryCount={}",
                event.provider(),
                event.operation(),
                event.sessionId(),
                event.turn(),
                event.requestId(),
                event.idempotencyKey() == null ? "-" : event.idempotencyKey(),
                event.latencyMs(),
                event.outcome(),
                event.retryCount()
        );
    }
}
