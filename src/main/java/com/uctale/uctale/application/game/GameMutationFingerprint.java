package com.uctale.uctale.application.game;

import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

@Component
public class GameMutationFingerprint {

    public String init(GameInitRequest request) {
        return sha256(join("init", normalize(request.worldSetting()), normalize(request.characterSetting())));
    }

    public String progress(GameProgressRequest request) {
        boolean legacyWireRequest = request.actionToken() == null
                && request.actionType() == null
                && request.sourceTurn() == null
                && request.arguments() == null;
        if (legacyWireRequest) {
            return sha256(join(
                    "progress",
                    Long.toString(request.sessionId()),
                    Integer.toString(request.choiceId()),
                    Integer.toString(request.expectedTurn())
            ));
        }

        return sha256(join(
                "progress",
                Long.toString(request.sessionId()),
                Integer.toString(request.choiceId()),
                Integer.toString(request.expectedTurn()),
                nullable(request.actionToken()),
                nullable(request.actionType()),
                request.sourceTurn() == null ? "" : Integer.toString(request.sourceTurn()),
                canonicalArguments(request.arguments())
        ));
    }

    private String canonicalArguments(Map<String, String> arguments) {
        if (arguments == null || arguments.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(arguments).forEach((key, value) -> builder
                .append(key.length()).append(':').append(key)
                .append('=')
                .append(value == null ? -1 : value.length()).append(':').append(value == null ? "" : value)
                .append('|'));
        return builder.toString();
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append(part.length()).append(':').append(part).append('|');
        }
        return builder.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
