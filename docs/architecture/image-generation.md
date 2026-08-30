# 이미지 생성 계약

## 책임 경계

브라우저는 Pollinations prompt나 provider URL을 만들지 않는다. Narrative turn에서 서버가 versioned prompt를 만들고 server-issued image asset을 발급한다. asset에는 최초 발급 시점의 생성 계약을 고정한다.

- prompt
- model
- width / height
- seed
- safe
- style version

이 값은 `image_asset`에 저장되므로 운영 설정이 바뀌어도 이미 발급된 asset의 재시도 요청은 바뀌지 않는다.

## 기본 정책

실제 benchmark 전환 근거가 마련되기 전까지 model과 생성 해상도는 production 값인 `flux`, `768x432`를 유지한다. 원본보다 큰 CSS 표시로 생기던 소폭 확대는 프론트 표시 폭을 768px로 제한해 제거한다.

- model: `flux`
- landscape: `768x432`
- square: `512x512`
- safe: `true`
- style: `uctale-charcoal-v2`
- connect timeout: 10초
- read timeout: 120초
- provider retry: 최대 2회
- max response: 8 MiB
- 허용 MIME: JPEG, PNG

설정은 `GAME_IMAGE_*` 환경변수로 조정할 수 있다. model/size/style 변경은 새로 발급되는 asset에만 적용된다.

## Charcoal style version

### `uctale-charcoal-v2` — 신규 기본값

production smoke test에서 핵폭발·불꽃처럼 색채 의미가 강한 장면이 v1의 후행 style suffix를 압도해 컬러 일러스트로 드리프트하는 사례가 확인됐다. v2는 scene을 삭제하거나 프론트에서 흑백 filter로 덮지 않고 prompt 계약 자체를 강화한다.

- prompt 시작에서 `monochrome charcoal and graphite`, `grayscale only`, off-white paper와 hand-drawn medium을 먼저 선언한다.
- colored pigment/accent, watercolor, oil painting, digital color painting, photorealism, 3D render를 명시적으로 제외한다.
- scene의 fire/explosion/neon/sunset/glowing object 표현은 그대로 남긴다.
- prompt 끝에서 해당 요소를 black/gray/white tonal value로만 표현하도록 final monochrome lock을 다시 건다.
- atmosphere/composition 문구는 generic cinematic color concept-art보다 narrative/editorial hand-drawn 방향을 사용한다.

이 계약은 provider의 확률적 결과를 100% 보장하지는 않지만, scene token보다 style을 우선 배치하고 앞뒤에서 반복해 Flux의 style drift 가능성을 낮춘다.

### `uctale-charcoal-v1` — legacy compatibility

v1 preset과 prompt 조합 로직은 삭제하지 않는다. 이미 발급된 asset에는 prompt와 styleVersion이 DB에 저장되어 있으므로, v1 asset 재조회/재시도는 당시의 model·prompt·size·seed·safe·styleVersion을 그대로 사용한다.

따라서 v2 전환은 **새 asset에만 적용**되며 과거 이미지의 재현 계약을 변경하지 않는다.

## Pollinations 요청

- provider secret은 `Authorization: Bearer`로만 전달한다.
- public asset URL, query string, 구조화 로그에 secret을 넣지 않는다.
- `model`, `width`, `height`, `seed`, `safe`를 항상 명시한다.
- 공식 문서에 없는 `nologo` 옵션은 사용하지 않는다.
- raw prompt는 구조화 로그에 남기지 않고 SHA-256 hash만 사용한다.

## 실패와 재시도

자동 재시도 대상:

- 네트워크/timeout
- HTTP 429
- HTTP 502
- HTTP 503

자동 재시도하지 않는 오류:

- 400 잘못된 요청
- 401 인증 실패
- 402 잔액/예산 부족
- 403 권한 실패
- 422 content policy 또는 처리 불가 요청

`Retry-After`가 delta-seconds 또는 HTTP-date로 제공되면 그 값을 우선하고, 없으면 짧은 bounded backoff를 사용한다. 모든 retry는 asset에 저장된 동일 model/prompt/size/seed/safe를 사용한다.

응답은 non-empty JPEG/PNG이며 설정된 최대 byte 이하인 경우에만 저장한다. 최종 생성 실패는 canonical GameState나 GameLog turn을 롤백하지 않는다. 이미지 조회에는 정적 SVG placeholder를 반환하고 이후 조회에서 다시 생성할 수 있게 asset은 미생성 상태로 유지한다.

## 관측성과 비용

`provider_call` 공통 telemetry와 `image_provider_result` 이미지 진단 로그를 함께 사용한다. Pollinations 실제 retry 횟수는 성공 결과/최종 실패에서 공통 telemetry로 전달되며, 이미지 진단 로그에는 다음 항목을 한 event에 기록한다.

- asset ID / session / turn
- SHA-256 prompt hash
- model / size / seed / style version
- latency
- provider status / error code / requestId
- response MIME / byte size
- retry count

API key와 raw prompt는 기록하지 않는다.

실제 비용은 Pollinations account usage에서 기간과 key를 기준으로 확인하고, `docs/benchmarks/pollinations-image-benchmark.md`의 request 결과와 대조한다. 기존 #50 benchmark 수치는 v1에서 model/resolution을 비교한 역사적 기록이므로 v2 전환으로 덮어쓰지 않는다. 재검증이 필요하면 benchmark script의 v2 chromatic stress fixture를 별도 output으로 실행한다.

## 한계

- 동일 asset의 JVM 내 최초 생성은 generation lock으로 단일화하지만 multi-instance 전역 단일화는 보장하지 않는다.
- benchmark 실행은 실제 provider 비용을 발생시키므로 CI에서 자동 실행하지 않는다.
- prompt 강화만으로 provider의 시각 결과를 완전 결정할 수는 없다.
- 자동 색상 분석/재생성, 캐릭터 외형 고정, reference image, object storage/CDN은 이 단계 범위가 아니다.
