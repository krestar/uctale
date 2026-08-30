# 서버 발급 이미지 asset

## 목적

UCTale의 이미지 provider는 브라우저가 전달한 임의 prompt를 직접 실행하지 않는다. 게임 서버가 Narrative 결과에서 확정한 시각 정보로 prompt를 구성하고, 서버가 발급한 `imageAssetId`만 클라이언트에 노출한다.

## 요청 흐름

1. `GameService`가 Narrative의 `visualAssets`에서 이미지 prompt를 구성한다.
2. 서버가 UUID 기반 asset ID와 `/api/game/image-assets/{assetId}` URL을 발급한다.
3. `GamePersistenceService`가 같은 transaction에서 asset을 `GameSession`과 turn에 연결하고 `GameLog.imageUrl`에는 asset URL만 저장한다.
4. 브라우저는 asset URL만 GET 한다. prompt와 provider URL은 브라우저 요청에 포함되지 않는다.
5. `ImageAssetService`는 `assetId + session.ownerKey`로 조회한 경우에만 provider를 호출한다.

## 소유권과 오류 정책

- asset은 반드시 하나의 `GameSession` 및 발급 turn에 속한다.
- 다른 접근 주체가 유효한 asset ID를 알아도 조회 결과는 `404 IMAGE_ASSET_NOT_FOUND`다.
- 존재하지 않는 asset과 다른 owner의 asset은 같은 404 계약을 사용해 존재 여부 노출을 줄인다.
- `/api/game/image-assets/**`는 access-session interceptor와 `X-UCTale-Client: web` 검증을 동일하게 적용한다.

## 중복 호출 정책

- 최초 정상 생성 결과의 bytes와 content type을 `image_asset`에 저장한다.
- 이후 같은 asset 요청은 저장된 결과를 반환하며 provider를 다시 호출하지 않는다.
- 같은 JVM에서 동시에 들어오는 최초 요청은 asset별 in-process lock으로 한 번만 생성하도록 억제한다.
- provider 생성이 실패하면 결과를 저장하지 않으며 이후 같은 asset 요청은 다시 시도할 수 있다.
- 현재 공유 베타는 단일 application instance를 전제로 한다. 여러 application instance가 같은 미생성 asset을 정확히 동시에 요청하는 경우 provider 호출의 완전한 단일화는 보장하지 않는다. 이 한계는 향후 분산 reservation/worker 도입 시 다룬다.

## 이미지 재사용

Narrative가 새 시각 요소를 제공하지 않는 turn은 새 asset을 만들지 않고 이전 `GameLog.imageUrl`을 그대로 재사용한다. 시각 변화가 있는 turn만 새 asset을 발급한다.

## 기존 데이터

#25 이전의 익명 세션은 소유권 migration 정책에 따라 이미 접근 불가능하다. #26 이후 새로 생성되는 세션은 모두 asset 기반 URL을 사용한다. 배포 직전 #25 방식으로 생성된 세션의 legacy prompt URL은 보존 대상 save/resume 기능이 아직 제공되지 않는 현재 공유 베타 범위에서는 migration하지 않는다.

## 범위 밖

- 객체 스토리지/CDN
- 이미지 worker/queue
- distributed lock
- provider별 retry/backoff 및 최대 byte/MIME 정책 고도화
- 이미지 모델/seed/style version 최적화

이 항목들은 후속 이미지 운영 이슈에서 확장한다.
