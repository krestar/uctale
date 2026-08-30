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

현재 production 기본값은 benchmark 전환 근거가 마련되기 전까지 기존 `flux`를 유지한다.

- model: `flux`
- landscape: `1024x576`
- square: `768x768`
- safe: `true`
- style: `uctale-charcoal-v1`
- connect timeout: 10초
- read timeout: 120초
- provider retry: 최대 2회
- max response: 8 MiB
- 허용 MIME: JPEG, PNG

설정은 `GAME_IMAGE_*` 환경변수로 조정할 수 있다. model/size/style 변경은 새로 발급되는 asset에만 적용된다.

## Pollinations 요청

- provider secret은 `Authorization: Bearer`로만 전달한다.
- public asset URL, query string, 구조화 로그에 secret을 넣지 않는다.
- `model`, `width`, `height`, `seed`, `safe`를 항상 명시한다.
- 공식 문서에 없는 `nologo` 옵션은 사용하지 않는다.
- raw prompt는 구조화 로그에 남기지 않고 hash만 사용한다.

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

`Retry-After`가 초 단위로 제공되면 그 값을 우선하고, 없으면 짧은 bounded backoff를 사용한다. 모든 retry는 asset에 저장된 동일 model/prompt/size/seed/safe를 사용한다.

응답은 non-empty JPEG/PNG이며 설정된 최대 byte 이하인 경우에만 저장한다. 최종 생성 실패는 canonical GameState나 GameLog turn을 롤백하지 않는다. 이미지 조회에는 정적 SVG placeholder를 반환하고 이후 조회에서 다시 생성할 수 있게 asset은 미생성 상태로 유지한다.

## 관측성과 비용

Pollinations adapter 로그는 다음 진단 항목을 남긴다.

- prompt hash
- model / size / seed / safe / style version
- latency
- provider status / error code / requestId / Retry-After
- response MIME / byte size
- retry count

API key와 raw prompt는 기록하지 않는다. session/turn/requestId는 #27의 provider-call telemetry와 server-issued asset metadata를 통해 함께 추적한다.

실제 비용은 Pollinations account usage에서 기간과 key를 기준으로 확인하고, `docs/benchmarks/pollinations-image-benchmark.md`의 request 결과와 대조한다.

## 한계

- 동일 asset의 JVM 내 최초 생성은 generation lock으로 단일화하지만 multi-instance 전역 단일화는 보장하지 않는다.
- benchmark 실행은 실제 provider 비용을 발생시키므로 CI에서 자동 실행하지 않는다.
- 캐릭터 외형 고정, reference image, object storage/CDN은 이 단계 범위가 아니다.
