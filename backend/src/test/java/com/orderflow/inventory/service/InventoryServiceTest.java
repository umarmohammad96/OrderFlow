package com.orderflow.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.inventory.repository.InventoryRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    InventoryRepository repository;
    @InjectMocks
    InventoryService service;

    @Test
    void reservesInAscendingProductOrderToAvoidDeadlocks() {
        when(repository.reserve(anyLong(), anyInt())).thenReturn(1);
        Map<Long, Integer> request = new LinkedHashMap<>();
        request.put(30L, 1);
        request.put(10L, 2);
        request.put(20L, 3);

        service.reserve(request);

        InOrder order = inOrder(repository);
        order.verify(repository).reserve(10L, 2);
        order.verify(repository).reserve(20L, 3);
        order.verify(repository).reserve(30L, 1);
    }

    @Test
    void zeroAffectedRowsMeansOutOfStock() {
        when(repository.reserve(10L, 1)).thenReturn(1);
        when(repository.reserve(20L, 5)).thenReturn(0);

        assertThatThrownBy(() -> service.reserve(Map.of(10L, 1, 20L, 5, 30L, 1)))
                .isInstanceOf(OutOfStockException.class)
                .hasMessageContaining("20");
        // fails fast: later lines are not attempted (and the transaction rollback undoes line 10)
        verify(repository, never()).reserve(eq(30L), anyInt());
    }

    @Test
    void releasingMoreThanReservedIsAnInvariantViolation() {
        when(repository.release(10L, 3)).thenReturn(0);

        assertThatThrownBy(() -> service.release(Map.of(10L, 3))).isInstanceOf(IllegalStateException.class);
    }
}
