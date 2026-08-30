# Pollinations 이미지 benchmark

## 목적

UCTale 장면 삽화의 기본 model과 해상도를 감으로 바꾸지 않고 동일 prompt/seed 조건에서 비교하기 위한 절차다.

비교 대상:

- model: `flux`, `zimage`, `dreamshaper`
- resolution: `768x432`, `1024x576`
- fixture: 실내·실외, 단독 NPC, 다수 인물, 몬스터, 전투, 핵심 아이템, 장소 이동, 밝고 어두운 장면을 포함한 16개 장면
- seed: fixture별 고정 seed를 모든 model/resolution 조합에 동일 적용

## 현재 사전 판단

2026-08-30 공식 Pollinations 문서와 registry를 다시 확인했다.

- Pollinations의 현재 provider 기본 image model은 UCTale의 기존 명시값과 달리 `zimage`이므로 production에서는 model을 항상 명시해야 한다.
- `flux`, `zimage`, `dreamshaper`는 현재 image model 후보로 제공된다.
- `dreamshaper`는 registry에서 매우 낮은 비용과 빠른 생성이 강점이지만 세부 묘사는 단순하다고 설명된다.
- API는 model, width, height, seed, safe 입력과 Bearer 인증을 지원한다.

이 정보만으로 이미지 품질·해상도 우열을 단정할 수 없으므로 production 기본값은 기존 `flux`, `768x432`를 유지한다.

## 실행

실제 생성에는 계정 비용이 발생하므로 운영 secret을 저장소나 CI에 넣어 자동 실행하지 않는다.

```bash
POLLINATIONS_TOKEN=... python scripts/benchmark_pollinations_images.py
```

출력:

- `build/pollinations-benchmark/images/`: blind review용 이미지
- `build/pollinations-benchmark/raw.csv`: fixture/model/size/seed/status/latency/MIME/bytes
- `build/pollinations-benchmark/summary.json`: model/size별 성공률, 평균·P95 지연

스크립트는 token과 raw prompt를 결과 파일에 기록하지 않는다.

## Blind review

이미지 파일명을 reviewer에게 직접 보여주지 않고 임시 번호로 섞은 뒤 각 결과에 1~5점을 기록한다.

| 평가 항목 | 설명 |
| --- | --- |
| Prompt 준수도 | 인물·몬스터·사물·장소가 요청과 맞는가 |
| Charcoal style 일관성 | UCTale charcoal preset을 안정적으로 유지하는가 |
| 왜곡 | 인체, 사물, 구조물의 명백한 왜곡이 적은가 |
| 세부 묘사 | 게임 삽화로 사용할 만큼 장면 정보가 읽히는가 |
| 사용 가능 여부 | 별도 재생성 없이 바로 노출 가능한가 |

모델/해상도 선택 시 평균 점수만 보지 않고 오류율, 평균·P95 지연, Pollinations account usage의 실제 비용을 함께 기록한다. `사용 가능한 이미지 한 장당 비용 = 총 비용 / 사용 가능 판정 이미지 수`로 비교한다.

## 결과 기록 상태

이 구현 작업에서는 production Pollinations secret에 접근하지 않고 외부 유료 생성도 임의 실행하지 않았기 때문에 실제 이미지/지연/비용 점수는 기록하지 않았다. 따라서 benchmark가 실행되고 blind review 결과가 채워지기 전까지는 `flux`, `768x432`를 기본값으로 유지한다.

실행 후 아래 표를 채우고 기본값 변경 여부를 별도 PR에서 판단한다.

| Model | Resolution | Prompt | Style | Distortion | Detail | Error rate | Avg/P95 | Cost/image | Usable cost | 결정 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | --- |
| flux | 768x432 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | 현재 기본 |
| flux | 1024x576 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | 비교 |
| zimage | 768x432 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | 비교 |
| zimage | 1024x576 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | 비교 |
| dreamshaper | 768x432 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | 비교 |
| dreamshaper | 1024x576 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | 비교 |
