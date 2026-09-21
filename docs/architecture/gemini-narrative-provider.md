# Gemini Narrative provider 설정과 버전 호환 경계

## 목적

UCTale의 Narrative provider 모델 버전을 game domain이나 `NarrativeGenerator` port에 노출하지 않고 provider adapter 설정으로 관리합니다.

현재 production primary 기본 모델은 `gemini-3.7-flash`입니다. Google 공식 문서에서 stable Flash로 제공되며 structured outputs와 `low` / `medium` / `high` thinking level을 지원합니다. 일시적인 provider 408/429/5xx 또는 network failure가 발생하면 bounded recovery의 다음 실제 provider attempt에서 stable `gemini-3.6-flash` fallback을 사용할 수 있습니다.

모델 선택 근거와 당시 가격은 구현 PR에 기록합니다. 이 문서는 현재 runtime 계약을 설명하며 별도 자체 benchmark 결과를 주장하지 않습니다.

## 설정

`application.properties`의 기본값은 다음 환경변수로 override할 수 있습니다.

- `GOOGLE_AI_MODEL`: primary stable Flash model ID. 기본 `gemini-3.7-flash`
- `GOOGLE_AI_FALLBACK_MODEL`: transient provider failure용 stable Flash model ID. 기본 `gemini-3.6-flash`
- `GOOGLE_AI_THINKING_OPENING`: opening thinking level. 기본 `medium`
- `GOOGLE_AI_THINKING_PROGRESS`: progress thinking level. 기본 `low`
- `GOOGLE_AI_API_KEY`: Gemini API key
- `GOOGLE_AI_CONNECT_TIMEOUT_MS`: connect timeout. 기본 10,000ms
- `GOOGLE_AI_READ_TIMEOUT_MS`: read timeout. 기본 40,000ms
- `GAME_TURN_RESERVATION_LEASE_SECONDS`: progress reservation lease. 기본 180초
- `GAME_TURN_RECOVERY_COOLDOWN_SECONDS`: provider recovery cooldown. 기본 30초

`latest`, preview, experimental alias는 production 설정으로 허용하지 않습니다. primary와 fallback 모두 `gemini-{major}.{minor}-flash` 형태의 명시적인 stable Flash ID만 허용합니다.

## compatibility boundary

`GeminiProviderSettings`가 모델 버전과 REST request 차이를 adapter 내부에서 흡수합니다.

- 검증된 Gemini 3.x stable Flash: `generationConfig.thinkingConfig.thinkingLevel` 사용
- Gemini 2.5 Flash rollback: legacy `thinkingBudget` 사용
- 같은 Gemini 3.x 계약 안의 stable Flash 교체는 설정 변경만으로 가능
- 아직 검증하지 않은 새 major version은 동일 계약이라고 추측하지 않고 startup에서 거부하며, 공식 계약을 확인한 뒤 provider compatibility boundary만 갱신함
- 계약이 바뀌는 모델도 `GeminiProviderSettings` / `GeminiNarrativeAdapter` 내부에서만 호환 처리를 추가하며 game domain과 `NarrativeGenerator` port는 변경하지 않음

Gemini 2.5 rollback의 `low` / `medium` / `high`는 Google이 제공하는 동일 이름의 직접 매핑이 아닙니다. UCTALE compatibility policy로 다음 budget을 사용합니다.

- `low`: 1,024 tokens
- `medium`: `-1` dynamic thinking
- `high`: 24,576 tokens

이 정책은 rollback 시 설정 인터페이스를 유지하기 위한 provider-local 변환이며 게임 규칙에는 영향을 주지 않습니다.

## 요청 계약

Gemini request는 다음을 유지합니다.

- `generateContent` REST endpoint
- API key는 `x-goog-api-key` header로 전달
- `responseMimeType=application/json`
- response schema 기반 structured output
- opening과 progress의 thinking level 독립 적용
- malformed response repair 요청은 원 요청과 동일한 operation thinking level 및 primary model 사용
- transient provider failure retry는 원 prompt를 변경하지 않고 fallback model 사용

### transient provider recovery

다음 실패만 일시적 provider failure로 분류해 기존 bounded recovery에 포함합니다.

- HTTP 408
- HTTP 429
- HTTP 5xx
- connect/read I/O 등 `ResourceAccessException`

이 retry는 adapter 내부에서 숨겨진 추가 호출을 만들지 않습니다. 첫 호출과 각 retry는 각각 하나의 실제 provider attempt이며 `NarrativeRecoveryExecutor`의 retry count와 일치합니다. progress에서는 기존 `markProviderAttemptStarted` callback이 각 실제 provider attempt 전에 한 번씩 실행됩니다. 따라서 provider 호출이 재시도되어도 canonical game state는 narrative 성공 후 기존 commit 경계에서 한 번만 저장됩니다.

HTTP 429는 짧은 50ms/150ms backoff로 즉시 재호출하지 않습니다. 명시적 30초 recovery cooldown으로 전환하고 `503 NARRATIVE_PROVIDER_RECOVERY_WAIT`과 `Retry-After`를 반환합니다. 408/5xx/network failure는 현재 recovery window에서 최대 3회까지 bounded retry하며, 모두 소진되면 동일한 recovery wait 경계로 전환합니다. cooldown이 지난 뒤 새 reservation owner가 recovery window를 획득하면 다시 시도할 수 있습니다.

progress의 transient retry가 모두 소진되면 API는 영구 차단 대신 `503 NARRATIVE_PROVIDER_RECOVERY_WAIT`을 반환합니다. retry 대상이 아닌 provider 4xx는 반복하지 않고 `502 NARRATIVE_PROVIDER_FAILURE`로 변환합니다. malformed/invalid structured response recovery 소진은 기존 `502 PROVIDER_RESPONSE_INVALID` 계약을 유지합니다.

외부 provider 자체의 strict exactly-once는 보장하지 않습니다.

### Narrative 출력 언어 계약

사용자에게 노출되는 Narrative 필드와 image provider용 시각 묘사의 언어 책임을 분리합니다.

- `title`, `story_text`, `choices[].text`는 세계관/캐릭터 설정과 현재 narrative context에서 확립된 주 언어를 유지합니다.
- 세계관/캐릭터 설정의 주 언어가 한국어이면 opening의 `title`, `story_text`, `choices[].text`를 한국어로 작성하도록 명시합니다.
- progress는 `worldPremise`, `playerDescription`, Story Memory와 최근 narrative context의 주 언어를 계속 사용하도록 명시합니다.
- malformed response repair는 원 요청에서 확립된 Narrative 주 언어를 그대로 유지하며 번역하거나 다른 언어로 전환하지 않습니다.
- transient provider retry는 원 prompt 자체를 다시 사용하므로 별도 repair 지시를 추가하지 않습니다.
- `visual_assets.background`, `visual_assets.characters`, `visual_assets.assets`는 기존 image prompt pipeline과의 호환성을 위해 항상 영어 설명을 사용합니다.
- JSON field 이름과 `visual_assets`의 영어 계약은 사용자 노출 Narrative를 영어로 전환할 근거가 아닙니다.

이 계약은 prompt 지침이며 별도 외부 언어 감지/번역 서비스를 도입하지 않습니다. 또한 언어 선택은 canonical game state나 Skill Check/GameResult 판정 권한을 LLM에 부여하지 않습니다.

## 관측성

기존 operation-level `provider_call` event는 configured primary Narrative model ID와 전체 bounded recovery의 retry/attempt count를 기록합니다. Gemini adapter는 실제 provider attempt별 `gemini_provider_result` 구조화 로그에 다음을 기록합니다.

- 실제 호출 model(primary 또는 fallback)
- thinkingLevel
- context (`opening`, `opening_repair`, `progress`, `progress_repair`)
- latencyMs
- outcome
- promptTokens
- candidatesTokens
- thoughtsTokens
- totalTokens

따라서 fallback을 사용한 요청에서는 operation-level event의 model은 primary를 나타내고, attempt별 실제 모델은 adapter 로그에서 구분합니다. 토큰 수는 Gemini `usageMetadata`가 제공될 때만 기록됩니다. prompt/story 전문과 API key는 기록하지 않습니다.

bounded recovery와 canonical commit 경계는 기존 정책을 그대로 유지합니다. 모델 설정이나 Narrative 출력 언어 계약은 canonical game state 저장 의미를 변경하지 않습니다.

## rollback

production에서 문제가 발생하면 코드 변경 없이 `GOOGLE_AI_MODEL`, `GOOGLE_AI_FALLBACK_MODEL`과 필요한 thinking 설정을 검증된 stable Flash로 변경해 rollback할 수 있습니다.

rollback 후에는 최소한 다음을 확인합니다.

- 한국어 설정의 opening title/story/choices가 한국어로 유지되는지
- progress와 malformed response repair에서 Narrative 언어가 유지되는지
- transient failure에서 fallback model로 bounded retry 되는지
- fallback 소진 시 generic 500이 아니라 provider-specific error가 반환되는지
- `visual_assets` 영어 설명 계약
- structured output validation
- provider telemetry의 attempt count와 adapter model/thinking 로그

## 공식 참고

- Google Gemini models: https://ai.google.dev/gemini-api/docs/models
- Gemini 3.7 Flash: https://ai.google.dev/gemini-api/docs/models/gemini-3.7-flash
- Gemini 3.6 Flash: https://ai.google.dev/gemini-api/docs/models/gemini-3.6-flash
- Thinking: https://ai.google.dev/gemini-api/docs/generate-content/thinking
- Pricing: https://ai.google.dev/gemini-api/docs/pricing
