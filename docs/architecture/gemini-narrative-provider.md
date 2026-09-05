# Gemini Narrative provider 설정과 버전 호환 경계

## 목적

UCTale의 Narrative provider 모델 버전을 game domain이나 `NarrativeGenerator` port에 노출하지 않고 provider adapter 설정으로 관리합니다.

현재 production 기본 모델은 `gemini-3.7-flash`입니다. 2026-09-01 기준 Google 공식 문서에서 GA stable이며 production-ready Flash로 안내되고, structured outputs와 `low` / `medium` / `high` thinking level을 지원합니다.

모델 선택 근거와 당시 가격은 구현 PR에 기록합니다. 이 문서는 현재 runtime 계약을 설명하며 별도 자체 benchmark 결과를 주장하지 않습니다.

## 설정

`application.properties`의 기본값은 다음 환경변수로 override할 수 있습니다.

- `GOOGLE_AI_MODEL`: 명시적인 stable Flash model ID. 기본 `gemini-3.7-flash`
- `GOOGLE_AI_THINKING_OPENING`: opening thinking level. 기본 `medium`
- `GOOGLE_AI_THINKING_PROGRESS`: progress thinking level. 기본 `low`
- `GOOGLE_AI_API_KEY`: Gemini API key

`latest`, preview, experimental alias는 production 설정으로 허용하지 않습니다. 모델 ID는 `gemini-{major}.{minor}-flash` 형태의 명시적인 stable Flash ID만 허용합니다.

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
- repair 요청은 원 요청과 동일한 operation thinking level 사용

### Narrative 출력 언어 계약

사용자에게 노출되는 Narrative 필드와 image provider용 시각 묘사의 언어 책임을 분리합니다.

- `title`, `story_text`, `choices[].text`는 세계관/캐릭터 설정과 현재 narrative context에서 확립된 주 언어를 유지합니다.
- 세계관/캐릭터 설정의 주 언어가 한국어이면 opening의 `title`, `story_text`, `choices[].text`를 한국어로 작성하도록 명시합니다.
- progress는 `worldPremise`, `playerDescription`, Story Memory와 최근 narrative context의 주 언어를 계속 사용하도록 명시합니다.
- repair는 원 요청에서 확립된 Narrative 주 언어를 그대로 유지하며 번역하거나 다른 언어로 전환하지 않습니다.
- `visual_assets.background`, `visual_assets.characters`, `visual_assets.assets`는 기존 image prompt pipeline과의 호환성을 위해 항상 영어 설명을 사용합니다.
- JSON field 이름과 `visual_assets`의 영어 계약은 사용자 노출 Narrative를 영어로 전환할 근거가 아닙니다.

이 계약은 prompt 지침이며 별도 외부 언어 감지/번역 서비스를 도입하지 않습니다. 또한 언어 선택은 canonical game state나 Skill Check/GameResult 판정 권한을 LLM에 부여하지 않습니다.

## 관측성

기존 `provider_call` event는 설정된 Narrative model ID를 기록합니다. Gemini adapter는 provider attempt별 `gemini_provider_result` 구조화 로그에 다음을 추가로 기록합니다.

- model
- thinkingLevel
- context (`opening`, `opening_repair`, `progress`, `progress_repair`)
- latencyMs
- outcome
- promptTokens
- candidatesTokens
- thoughtsTokens
- totalTokens

토큰 수는 Gemini `usageMetadata`가 제공될 때만 기록됩니다. prompt/story 전문과 API key는 기록하지 않습니다.

bounded recovery와 canonical commit 경계는 #35에서 확립한 정책을 그대로 유지합니다. 모델 설정이나 Narrative 출력 언어 계약은 provider attempt 횟수나 게임 상태 저장 의미를 변경하지 않습니다.

## rollback

production에서 문제가 발생하면 코드 변경 없이 `GOOGLE_AI_MODEL`과 필요한 thinking 설정을 이전 stable Flash로 변경해 rollback할 수 있습니다.

rollback 후에는 최소한 다음을 확인합니다.

- 한국어 설정의 opening title/story/choices가 한국어로 유지되는지
- progress와 repair에서 Narrative 언어가 유지되는지
- `visual_assets` 영어 설명 계약
- structured output validation
- invalid response bounded recovery
- provider telemetry의 model/thinking 값

## 공식 참고

- Google Gemini models: https://ai.google.dev/gemini-api/docs/models
- Gemini 3.7 Flash: https://ai.google.dev/gemini-api/docs/models/gemini-3.7-flash
- Gemini 3.7 migration guide: https://ai.google.dev/gemini-api/docs/latest-model
- Thinking: https://ai.google.dev/gemini-api/docs/generate-content/thinking
- Pricing: https://ai.google.dev/gemini-api/docs/pricing