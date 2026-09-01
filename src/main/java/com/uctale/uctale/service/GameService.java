package com.uctale.uctale.service;

import com.uctale.uctale.application.cost.CostOperation;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationFingerprint;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.GameResponseProjector;
import com.uctale.uctale.application.game.GameTurnCommit;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.game.TurnProcessor;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.application.narrative.InvalidNarrativeResponseException;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeRecoveryExhaustedException;
import com.uctale.uctale.application.narrative.NarrativeRecoveryExecutor;
import com.uctale.uctale.application.narrative.NarrativeRecoveryInterruptedException;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class GameService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_STORY_LENGTH = 50_000;
    private static final int MAX_CHOICE_TEXT_LENGTH = 255;
    private static final int MAX_CHOICES = 8;
    private static final int MAX_IMAGE_PROMPT_LENGTH = 2_000;

    private final NarrativeGenerator narrativeGenerator;
    private final ImageAssetService imageAssetService;
    private final GamePersistenceService gamePersistenceService;
    private final ChoiceCodec choiceCodec;
    private final TurnProcessor turnProcessor;
    private final ImagePromptComposer imagePromptComposer;
    private final CostRateLimiter costRateLimiter;
    private final ProviderCallTelemetry providerCallTelemetry;
    private final GameMutationFingerprint mutationFingerprint;
    private final GameMutationRequestService mutationRequestService;
    private final NarrativeRecoveryExecutor narrativeRecoveryExecutor = NarrativeRecoveryExecutor.production();

    @Autowired
    public GameService(
            NarrativeGenerator narrativeGenerator,
            ImageAssetService imageAssetService,
            GamePersistenceService gamePersistenceService,
            ChoiceCodec choiceCodec,
            TurnProcessor turnProcessor,
            ImagePromptComposer imagePromptComposer,
            CostRateLimiter costRateLimiter,
            ProviderCallTelemetry providerCallTelemetry,
            GameMutationFingerprint mutationFingerprint,
            GameMutationRequestService mutationRequestService
    ) {
        this.narrativeGenerator = narrativeGenerator;
        this.imageAssetService = imageAssetService;
        this.gamePersistenceService = gamePersistenceService;
        this.choiceCodec = choiceCodec;
        this.turnProcessor = turnProcessor;
        this.imagePromptComposer = imagePromptComposer;
        this.costRateLimiter = costRateLimiter;
        this.providerCallTelemetry = providerCallTelemetry;
        this.mutationFingerprint = mutationFingerprint;
        this.mutationRequestService = mutationRequestService;
    }

    public GameService(
            NarrativeGenerator narrativeGenerator,
            ImageAssetService imageAssetService,
            GamePersistenceService gamePersistenceService,
            ChoiceCodec choiceCodec,
            ImagePromptComposer imagePromptComposer,
            CostRateLimiter costRateLimiter,
            ProviderCallTelemetry providerCallTelemetry,
            GameMutationFingerprint mutationFingerprint,
            GameMutationRequestService mutationRequestService
    ) {
        this(
                narrativeGenerator,
                imageAssetService,
                gamePersistenceService,
                choiceCodec,
                new TurnProcessor(),
                imagePromptComposer,
                costRateLimiter,
                providerCallTelemetry,
                mutationFingerprint,
                mutationRequestService
        );
    }

    public GameResponse initGame(String ownerKey, GameInitRequest request) {
        return initGame(CostRequestContext.internal(ownerKey, null, 1), request);
    }

    public GameResponse initGame(CostRequestContext costContext, GameInitRequest request) {
        GameMutationRequestService.BeginResult mutation = mutationRequestService.begin(
                costContext.ownerKey(), GameMutationRequestService.INIT, costContext.idempotencyKey(), null, null,
                mutationFingerprint.init(request)
        );
        if (mutation.replay()) {
            return replay(costContext.ownerKey(), mutation);
        }

        try {
            costRateLimiter.check(CostOperation.NARRATIVE, costContext);
            NarrativeTurn opening = observeOpening(costContext, request).turn();
            validateNarrativeTurn(opening);
            List<GameChoice> choices = choiceCodec.issue(opening.choices(), 1);

            String imagePrompt = imagePromptComposer.compose(opening.visualAssets());
            if (imagePrompt == null || imagePrompt.isBlank()) {
                imagePrompt = imagePromptComposer.composeFallback(request.worldSetting());
            }
            validateImagePrompt(imagePrompt);
            ImageAssetService.AssetReference imageAsset = imageAssetService.issue(imagePrompt, "16:9");

            var session = gamePersistenceService.saveOpening(
                    costContext.ownerKey(), request.worldSetting(), request.characterSetting(), opening.storyText(),
                    choiceCodec.serialize(choices), imageAsset, mutation.requestId(), opening.title()
            );
            GameState initialState = GameState.initial(request.worldSetting(), request.characterSetting(), opening.storyText());
            return GameResponseProjector.project(
                    session.getId(), session.getCurrentTurn(), opening.title(), opening.storyText(), choices,
                    imageAsset.publicUrl(), initialState, null
            );
        } catch (RuntimeException exception) {
            mutationRequestService.markFailed(mutation.requestId());
            throw exception;
        }
    }

    public GameResponse progressGame(String ownerKey, GameProgressRequest request) {
        return progressGame(CostRequestContext.internal(ownerKey, request.sessionId(), request.expectedTurn() + 1), request);
    }

    public GameResponse progressGame(CostRequestContext costContext, GameProgressRequest request) {
        GameMutationRequestService.BeginResult mutation = mutationRequestService.begin(
                costContext.ownerKey(), GameMutationRequestService.PROGRESS, costContext.idempotencyKey(),
                request.sessionId(), request.expectedTurn(), mutationFingerprint.progress(request)
        );
        if (mutation.replay()) {
            return replay(costContext.ownerKey(), mutation);
        }

        try {
            GamePersistenceService.LoadedTurn loadedTurn = gamePersistenceService.loadLatestTurn(
                    costContext.ownerKey(), request.sessionId(), request.expectedTurn()
            );
            CostRequestContext providerContext = new CostRequestContext(
                    costContext.requestId(), costContext.ownerKey(), costContext.clientIp(), loadedTurn.sessionId(),
                    request.expectedTurn() + 1, costContext.idempotencyKey()
            );

            PlayerAction playerAction = choiceCodec.resolve(loadedTurn.choicesJson(), request);
            TurnResolution resolution = turnProcessor.resolve(
                    loadedTurn.gameState(), playerAction, mutation.requestId(), mutation.reservationOwner()
            );
            String userChoiceText = resolution.gameResult().resolvedAction().displayText();
            String canonicalResultId = canonicalResultId(mutation.requestId(), loadedTurn.sessionId(),
                    resolution.stateTransition().nextState().turnNumber());
            NarrativeContext narrativeContext = NarrativeContext.from(canonicalResultId, resolution);
            costRateLimiter.check(CostOperation.NARRATIVE, providerContext);
            NarrativeTurn nextTurn = observeProgress(providerContext, mutation, narrativeContext).turn();
            validateNarrativeTurn(nextTurn);
            String generatedStoryId = "story:" + UUID.randomUUID();
            StateTransition committedTransition = turnProcessor.attachNarrative(resolution, nextTurn.storyText());
            List<GameChoice> choices = choiceCodec.issue(nextTurn.choices(), request.expectedTurn() + 1);

            ImageAssetService.AssetReference imageAsset = null;
            String imageUrl = loadedTurn.imageUrl();
            String imagePrompt = imagePromptComposer.compose(nextTurn.visualAssets());
            if (imagePrompt != null && !imagePrompt.isBlank()) {
                validateImagePrompt(imagePrompt);
                log.info("새로운 이미지 asset 발급");
                imageAsset = imageAssetService.issue(imagePrompt, "16:9");
                imageUrl = imageAsset.publicUrl();
            } else {
                log.info("시각적 변화 없음 -> 이전 이미지 asset 재사용");
            }

            GameTurnCommit commit = new GameTurnCommit(
                    request.expectedTurn(), resolution.gameResult().resolvedAction().legacyChoiceId(), userChoiceText,
                    committedTransition, nextTurn.storyText(), choiceCodec.serialize(choices),
                    canonicalResultId, generatedStoryId, resolution.gameResult().skillCheckResult(), imageAsset
            );

            int savedTurn = gamePersistenceService.saveNextTurn(
                    costContext.ownerKey(), loadedTurn.sessionId(), commit, mutation.requestId(), nextTurn.title(),
                    mutation.reservationOwner()
            );
            return GameResponseProjector.project(
                    loadedTurn.sessionId(), savedTurn, nextTurn.title(), nextTurn.storyText(), choices, imageUrl,
                    committedTransition.nextState(), resolution.gameResult().skillCheckResult()
            );
        } catch (RuntimeException exception) {
            mutationRequestService.markFailed(mutation.requestId(), mutation.reservationOwner());
            throw exception;
        }
    }

    private NarrativeRecoveryExecutor.Result observeOpening(
            CostRequestContext costContext,
            GameInitRequest request
    ) {
        try {
            return providerCallTelemetry.observe(
                    "gemini", "opening", costContext,
                    () -> narrativeRecoveryExecutor.execute(
                            () -> narrativeGenerator.createOpening(
                                    request.worldSetting(), request.characterSetting()
                            ),
                            reasonCode -> narrativeGenerator.repairOpening(
                                    request.worldSetting(), request.characterSetting(), reasonCode
                            ),
                            () -> {}
                    ),
                    NarrativeRecoveryExecutor.Result::retryCount,
                    this::narrativeRetryCountFromFailure
            );
        } catch (NarrativeRecoveryInterruptedException exception) {
            throw exception.originalCause();
        }
    }

    private NarrativeRecoveryExecutor.Result observeProgress(
            CostRequestContext providerContext,
            GameMutationRequestService.BeginResult mutation,
            NarrativeContext narrativeContext
    ) {
        try {
            return providerCallTelemetry.observe(
                    "gemini", "progress", providerContext,
                    () -> mutationRequestService.markProviderAttemptStarted(
                            mutation.requestId(), mutation.reservationOwner()
                    ),
                    () -> narrativeRecoveryExecutor.execute(
                            () -> narrativeGenerator.createNextTurn(narrativeContext),
                            reasonCode -> narrativeGenerator.repairNextTurn(narrativeContext, reasonCode),
                            () -> mutationRequestService.markProviderAttemptStarted(
                                    mutation.requestId(), mutation.reservationOwner()
                            )
                    ),
                    NarrativeRecoveryExecutor.Result::retryCount,
                    this::narrativeRetryCountFromFailure
            );
        } catch (NarrativeRecoveryInterruptedException exception) {
            throw exception.originalCause();
        }
    }

    private int narrativeRetryCountFromFailure(RuntimeException exception) {
        if (exception instanceof NarrativeRecoveryExhaustedException exhausted) {
            return exhausted.retryCount();
        }
        if (exception instanceof NarrativeRecoveryInterruptedException interrupted) {
            return interrupted.retryCount();
        }
        return 0;
    }

    private GameResponse replay(String ownerKey, GameMutationRequestService.BeginResult mutation) {
        if (mutation.resultSessionId() == null || mutation.resultTurn() == null || mutation.resultTitle() == null) {
            throw new IllegalStateException("완료된 mutation request의 결과 참조가 올바르지 않습니다.");
        }
        GamePersistenceService.CommittedTurn turn = gamePersistenceService.loadCommittedTurn(
                ownerKey, mutation.resultSessionId(), mutation.resultTurn()
        );
        return GameResponseProjector.projectReplay(
                mutation.resultSessionId(), mutation.resultTurn(), mutation.resultTitle(), turn.storyText(),
                choiceCodec.deserialize(turn.choicesJson()), turn.imageUrl(), turn.gameState(), turn.skillCheckAudit()
        );
    }

    private String canonicalResultId(Long mutationRequestId, Long sessionId, int nextTurn) {
        if (mutationRequestId == null || sessionId == null || nextTurn < 2) {
            throw new IllegalArgumentException("canonical result link를 만들 수 없습니다.");
        }
        return "game-result:%d:%d:%d".formatted(sessionId, nextTurn, mutationRequestId);
    }

    private void validateNarrativeTurn(NarrativeTurn turn) {
        if (turn == null) throw new InvalidNarrativeResponseException("Narrative 응답이 없습니다.");
        if (turn.title() == null || turn.title().isBlank() || turn.title().length() > MAX_TITLE_LENGTH) {
            throw new InvalidNarrativeResponseException("Narrative 제목이 올바르지 않습니다.");
        }
        if (turn.storyText() == null || turn.storyText().isBlank() || turn.storyText().length() > MAX_STORY_LENGTH) {
            throw new InvalidNarrativeResponseException("Narrative 본문이 올바르지 않습니다.");
        }
        if (turn.choices() == null || turn.choices().isEmpty() || turn.choices().size() > MAX_CHOICES) {
            throw new InvalidNarrativeResponseException("Narrative 선택지 수가 올바르지 않습니다.");
        }
        Set<Integer> choiceIds = new HashSet<>();
        for (NarrativeTurn.Choice choice : turn.choices()) {
            if (choice == null || choice.id() <= 0 || !choiceIds.add(choice.id())) {
                throw new InvalidNarrativeResponseException("Narrative 선택지 ID가 올바르지 않습니다.");
            }
            if (choice.text() == null || choice.text().isBlank() || choice.text().length() > MAX_CHOICE_TEXT_LENGTH) {
                throw new InvalidNarrativeResponseException("Narrative 선택지 문구가 올바르지 않습니다.");
            }
        }
    }

    private void validateImagePrompt(String imagePrompt) {
        if (imagePrompt != null && imagePrompt.length() > MAX_IMAGE_PROMPT_LENGTH) {
            throw new InvalidNarrativeResponseException("이미지 prompt가 너무 깁니다.");
        }
    }
}
