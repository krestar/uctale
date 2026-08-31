package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameMutationRequestServiceTest {
    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Mock private GameMutationRequestRepository repository;
    @Mock private JdbcTemplate jdbcTemplate;

    private GameMutationRequestService service() {
        return new GameMutationRequestService(
                repository, jdbcTemplate,
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC), 90
        );
    }

    @Test
    void sameKeyWithDifferentFingerprint_IsConflict() {
        GameMutationRequest stored = new GameMutationRequest(OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "a".repeat(64));
        given(repository.findForUpdate(OWNER_KEY, "same-key-123")).willReturn(Optional.of(stored));
        assertThatThrownBy(() -> service().begin(OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "b".repeat(64)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void processingSameRequest_ReturnsInProgress() {
        GameMutationRequest stored = new GameMutationRequest(OWNER_KEY, "INIT", "same-key-123", null, null, "a".repeat(64));
        given(repository.findForUpdate(OWNER_KEY, "same-key-123")).willReturn(Optional.of(stored));
        assertThatThrownBy(() -> service().begin(OWNER_KEY, "INIT", "same-key-123", null, null, "a".repeat(64)))
                .isInstanceOf(MutationInProgressException.class);
    }

    @Test
    void failedRequestWithSameFingerprint_CanRetry() {
        GameMutationRequest stored = new GameMutationRequest(OWNER_KEY, "INIT", "retry-key-123", null, null, "a".repeat(64));
        stored.fail();
        given(repository.findForUpdate(OWNER_KEY, "retry-key-123")).willReturn(Optional.of(stored));
        GameMutationRequestService.BeginResult result = service().begin(
                OWNER_KEY, "INIT", "retry-key-123", null, null, "a".repeat(64));
        assertThat(result.replay()).isFalse();
        assertThat(stored.getStatus()).isEqualTo(GameMutationRequest.Status.PROCESSING);
        verify(repository).save(stored);
    }
}
