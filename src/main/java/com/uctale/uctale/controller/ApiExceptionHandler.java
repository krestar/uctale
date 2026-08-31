package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.RateLimitExceededException;
import com.uctale.uctale.application.game.GameSessionNotFoundException;
import com.uctale.uctale.application.game.IdempotencyConflictException;
import com.uctale.uctale.application.game.InvalidChoiceException;
import com.uctale.uctale.application.game.MutationInProgressException;
import com.uctale.uctale.application.game.PersistenceOperationException;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.application.image.ImageAssetNotFoundException;
import com.uctale.uctale.application.image.ImageGenerationException;
import com.uctale.uctale.application.narrative.InvalidNarrativeResponseException;
import com.uctale.uctale.security.AccessAuthenticationRateLimitExceededException;
import com.uctale.uctale.security.AccessRequestForbiddenException;
import com.uctale.uctale.security.AccessSessionException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AccessSessionException.class)
    public ResponseEntity<ApiError> handleAccessSession(AccessSessionException exception) { return error(HttpStatus.UNAUTHORIZED, exception.code(), exception.getMessage()); }
    @ExceptionHandler(AccessRequestForbiddenException.class)
    public ResponseEntity<ApiError> handleAccessRequestForbidden(AccessRequestForbiddenException exception) { return error(HttpStatus.FORBIDDEN, "ACCESS_REQUEST_FORBIDDEN", exception.getMessage()); }
    @ExceptionHandler(AccessAuthenticationRateLimitExceededException.class)
    public ResponseEntity<ApiError> handleAccessAuthenticationRateLimit(AccessAuthenticationRateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds())).body(new ApiError("ACCESS_RATE_LIMIT_EXCEEDED", exception.getMessage()));
    }
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds())).body(new ApiError("RATE_LIMIT_EXCEEDED", exception.getMessage()));
    }
    @ExceptionHandler(MutationInProgressException.class)
    public ResponseEntity<ApiError> handleMutationInProgress(MutationInProgressException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds())).body(new ApiError("MUTATION_IN_PROGRESS", exception.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage()); }
    @ExceptionHandler(GameSessionNotFoundException.class)
    public ResponseEntity<ApiError> handleSessionNotFound(GameSessionNotFoundException exception) { return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", exception.getMessage()); }
    @ExceptionHandler(ImageAssetNotFoundException.class)
    public ResponseEntity<ApiError> handleImageAssetNotFound(ImageAssetNotFoundException exception) { return error(HttpStatus.NOT_FOUND, "IMAGE_ASSET_NOT_FOUND", exception.getMessage()); }
    @ExceptionHandler(ImageGenerationException.class)
    public ResponseEntity<ApiError> handleImageGeneration(ImageGenerationException exception) { return error(HttpStatus.BAD_GATEWAY, "IMAGE_PROVIDER_FAILURE", "이미지를 생성하지 못했습니다."); }
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException exception) { return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception.getMessage()); }
    @ExceptionHandler(TurnConflictException.class)
    public ResponseEntity<ApiError> handleTurnConflict(TurnConflictException exception) { return error(HttpStatus.CONFLICT, "TURN_CONFLICT", exception.getMessage()); }
    @ExceptionHandler(InvalidChoiceException.class)
    public ResponseEntity<ApiError> handleInvalidChoice(InvalidChoiceException exception) { return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CHOICE", exception.getMessage()); }
    @ExceptionHandler(InvalidNarrativeResponseException.class)
    public ResponseEntity<ApiError> handleInvalidNarrativeResponse(InvalidNarrativeResponseException exception) { return error(HttpStatus.BAD_GATEWAY, "PROVIDER_RESPONSE_INVALID", "Narrative provider 응답이 올바르지 않습니다."); }
    @ExceptionHandler(PersistenceOperationException.class)
    public ResponseEntity<ApiError> handlePersistenceFailure(PersistenceOperationException exception) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTENCE_FAILURE", "게임 상태 저장 중 오류가 발생했습니다."); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst().map(error -> error.getDefaultMessage()).orElse("요청값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream().findFirst().map(violation -> violation.getMessage()).orElse("요청값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) { return ResponseEntity.status(status).body(new ApiError(code, message)); }
}
