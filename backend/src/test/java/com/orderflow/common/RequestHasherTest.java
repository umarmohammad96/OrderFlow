package com.orderflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.idempotency.RequestHasher;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

    @Test
    void producesStableHexSha256() {
        assertThat(RequestHasher.sha256("1x1")).hasSize(64).isEqualTo(RequestHasher.sha256("1x1"));
        assertThat(RequestHasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void differentPayloadsHashDifferently() {
        assertThat(RequestHasher.sha256("1x1")).isNotEqualTo(RequestHasher.sha256("1x2"));
    }
}
