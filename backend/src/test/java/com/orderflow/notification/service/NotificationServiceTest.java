package com.orderflow.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.notification.domain.NotificationChannel;
import com.orderflow.notification.repository.NotificationRepository;
import com.orderflow.payment.dto.OrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    JdbcTemplate jdbc;
    @Mock
    NotificationSender sender;
    @Mock
    NotificationRepository repository;
    @InjectMocks
    NotificationService service;

    private final OrderConfirmedEvent event = new OrderConfirmedEvent(UUID.randomUUID(), 42L, 7L, "Asha",
            "asha@example.com", new BigDecimal("10.00"), 2, "PAY-1", Instant.now());

    @Test
    void firstDeliverySendsEmailAndSms() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertThat(service.handleOrderConfirmed(event)).isEqualTo(2);
        verify(sender).send(eq(NotificationChannel.EMAIL), eq("asha@example.com"), anyString());
        verify(sender).send(eq(NotificationChannel.SMS), eq("user-7"), anyString());
    }

    @Test
    void redeliveredEventSendsNothing() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        assertThat(service.handleOrderConfirmed(event)).isZero();
        verify(sender, never()).send(any(), any(), any());
    }
}
