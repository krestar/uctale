package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class GameMutationRequestService {

    public static final String INIT = "INIT";
    public static final String PROGRESS = "PROGRESS";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final GameMutationRequestRepository repository;

    public GameMutationRequestService(GameMutationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BeginResult begin(
            String ownerKey,
            String operation,
            String idempotencyKey,
            Long sessionId,
            Integer expectedTurn,
            String fingerprint
    ) {
        validateKey(idempotencyKey);

        GameMutationRequest request = repository
                .findByOwnerKeyAndIdempotencyKey(ownerKey, idempotencyKey)
                .orElseGet(() -> repository.save(new GameMutationRequest(
                        ownerKey, operation, idempotencyKey, sessionId, expectedTurn, fingerprint
                )));

        if (!request.matches(operation, fingerprint)) {
            throw new IdempotencyConflictException("같은 Idempotency-Key가 다른 요청 payload에 재사용되었습니다.");
        }

        if (request.getStatus() == GameMutationRequest.Status.COMPLETED) {
            return BeginResult.replay(
                    request.getId(),
                    request.getResultSessionId(),
                    request.getResultTurn(),
                    request.getResultTitle()
            );
        }

        request.restart();
        repository.save(request);
        return BeginResult.process(request.getId());
    }

    @Transactional
    public void markFailed(Long requestId) {
        repository.findById(requestId).ifPresent(request -> {
            request.fail();
            repository.save(request);
        });
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key는 8~128자의 영문, 숫자, '.', '_', ':', '-'만 사용할 수 있습니다."
            );
        }
    }

    public record BeginResult(
            Long requestId,
            boolean replay,
            Long resultSessionId,
            Integer resultTurn,
            String resultTitle
    ) {
        static BeginResult process(Long requestId) {
            return new BeginResult(requestId, false, null, null, null);
        }

        static BeginResult replay(Long requestId, Long sessionId, Integer turn, String title) {
            return new BeginResult(requestId, true, sessionId, turn, title);
        }
    }
}
