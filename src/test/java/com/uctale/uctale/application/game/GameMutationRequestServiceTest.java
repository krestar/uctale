package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameMutationRequestServiceTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock
    private GameMutationRequestRepository repository;

    @Test
    void sameKeyWithDifferentFingerprint_IsConflict() {
        GameMutationRequest stored = new GameMutationRequest(
                OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "a".repeat(64)
        );
        given(repository.findByOwnerKeyAndIdempotencyKey(OWNER_KEY, "same-key-123"))
                .willReturn(Optional.of(stored));

        GameMutationRequestService service = new GameMutationRequestService(repository);

        assertThatThrownBy(() -> service.begin(
                OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "b".repeat(64)
        )).isInstanceOf(IdempotencyConflictException.class);

        verify(repository, never()).save(stored);
    }

    @Test
    void sameKeyAcrossOperations_IsConflict() {
        GameMutationRequest stored = new GameMutationRequest(
                OWNER_KEY, "INIT", "same-key-123", null, null, "a".repeat(64)
        );
        given(repository.findByOwnerKeyAndIdempotencyKey(OWNER_KEY, "same-key-123"))
                .willReturn(Optional.of(stored));

        GameMutationRequestService service = new GameMutationRequestService(repository);

        assertThatThrownBy(() -> service.begin(
                OWNER_KEY, "PROGRESS", "same-key-123", 42L, 1, "a".repeat(64)
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void failedRequestWithSameFingerprint_CanRetry() {
        GameMutationRequest stored = new GameMutationRequest(
                OWNER_KEY, "INIT", "retry-key-123", null, null, "a".repeat(64)
        );
        stored.fail();
        given(repository.findByOwnerKeyAndIdempotencyKey(OWNER_KEY, "retry-key-123"))
                .willReturn(Optional.of(stored));

        GameMutationRequestService service = new GameMutationRequestService(repository);
        GameMutationRequestService.BeginResult result = service.begin(
                OWNER_KEY, "INIT", "retry-key-123", null, null, "a".repeat(64)
        );

        assertThat(result.replay()).isFalse();
        assertThat(stored.getStatus()).isEqualTo(GameMutationRequest.Status.PROCESSING);
        verify(repository).save(stored);
    }
}
