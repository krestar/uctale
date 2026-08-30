package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.GameMutationRequest;
import com.uctale.uctale.repository.GameMutationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameMutationRequestService {

    public static final String INIT = "INIT";
    public static final String PROGRESS = "PROGRESS";

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
        GameMutationRequest request = repository
                .findByOwnerKeyAndOperationAndIdempotencyKey(ownerKey, operation, idempotencyKey)
                .orElseGet(() -> repository.save(new GameMutationRequest(
                        ownerKey, operation, idempotencyKey, sessionId, expectedTurn, fingerprint
                )));

        if (!request.hasFingerprint(fingerprint)) {
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

        if (request.getStatus() == GameMutationRequest.Status.PROCESSING && request.getId() != null
                && repository.findById(request.getId()).isPresent()) {
            // #30에서 active reservation/lease를 추가한다. 여기서는 완료된 retry 재사용과 payload 충돌만 보장한다.
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
