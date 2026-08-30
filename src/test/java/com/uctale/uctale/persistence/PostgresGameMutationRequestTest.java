package com.uctale.uctale.persistence;

import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import com.uctale.uctale.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostgresGameMutationRequestTest extends PostgresIntegrationTestSupport {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private GameMutationRequestRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("PostgreSQL은 owner/operation/idempotency key 중복 request를 unique constraint로 거부한다")
    void duplicateIdempotencyKey_IsRejectedByDatabase() {
        repository.saveAndFlush(new GameMutationRequest(
                OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "a".repeat(64)
        ));

        assertThatThrownBy(() -> repository.saveAndFlush(new GameMutationRequest(
                OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "b".repeat(64)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
