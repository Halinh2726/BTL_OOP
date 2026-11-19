package com.ptit.parking.shared.adapter.out.event;

import com.ptit.parking.shared.application.port.out.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Cài đặt {@link EventPublisher} bằng cơ chế sự kiện có sẵn của Spring (ApplicationEventPublisher). */
@Component
class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
