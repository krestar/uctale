# Pollinations 이미지 benchmark

## 목적

UCTale 장면 삽화의 기본 model과 해상도를 감으로 바꾸지 않고 동일 prompt/seed 조건에서 비교하기 위한 절차와 2026-08-30 실측 기록입니다.

비교 대상:

- model: `flux`, `zimage`, `dreamshaper`
- resolution: `768x432`, `1024x576`
- fixture: 실내·실외, 단독 NPC, 다수 인물, 몬스터, 전투, 핵심 아이템, 장소 이동, 밝고 어두운 장면을 포함한 16개 장면
- seed: fixture별 고정 seed를 모든 model/resolution 조합에 동일 적용

## 실행 조건

2026-08-30에 실제 Pollinations 계정으로 benchmark를 실행했습니다.

- 총 요청: 96 (`16 fixtures × 3 models × 2 resolutions`)
- 성공: 96
- 실패: 0
- 모든 조합에 같은 fixture prompt와 seed를 사용했습니다.
- 결과 파일에는 token과 raw prompt를 기록하지 않았습니다.
- Python `urllib` 기본 User-Agent가 provider에서 403으로 차단되는 것을 실측으로 발견해 benchmark client에 명시적 User-Agent/Accept를 추가했고, 401/402/403은 fail-fast하도록 보완했습니다.

## 성능 결과

| Model | Resolution | Success | Error rate | Avg latency | P95 latency | Avg response bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| flux | 768x432 | 16/16 | 0% | 7.63s | 9.36s | 210 KB |
| flux | 1024x576 | 16/16 | 0% | 8.13s | 9.15s | 323 KB |
| zimage | 768x432 | 16/16 | 0% | 8.13s | 8.95s | 155 KB |
| zimage | 1024x576 | 16/16 | 0% | 9.19s | 10.56s | 254 KB |
| dreamshaper | 768x432 | 16/16 | 0% | 6.53s | 7.45s | 46 KB |
| dreamshaper | 1024x576 | 16/16 | 0% | 6.42s | 7.15s | 47 KB |

## Blind review

모델/해상도 이름을 숨긴 A~F 시트로 다시 섞어 prompt 준수도, charcoal style 일관성, 왜곡, 세부 묘사와 별도 재생성 없이 게임 삽화로 사용할 수 있는지를 비교했습니다.

### flux

- UCTale의 rough charcoal sketch, 흑백, 거친 종이 질감과 story concept art 방향을 가장 안정적으로 유지했습니다.
- 전투, 몬스터, 다수 인물과 판타지 장면에서 focal point와 silhouette가 비교적 명확했습니다.
- 현실 장면 일부는 zimage보다 literal한 장소 표현이 약한 경우가 있었지만 전체적인 house style과 장면 가독성의 균형이 가장 좋았습니다.

### zimage

- 흑백 sketch 스타일과 공간 묘사가 안정적이고 일상/실내 장면의 prompt 준수도도 좋았습니다.
- flux 대비 평균 지연 시간이 길고 당시 단가도 더 높았으며, 판타지/액션 fixture에서 기본 모델 교체를 정당화할 만큼 일관된 품질 우위는 확인되지 않았습니다.

### dreamshaper

- 세 모델 중 가장 빠르고 응답 크기도 작았습니다.
- 요청한 charcoal sketch보다 어두운 cinematic/photorealistic 이미지로 치우치는 경우가 반복되어 UCTale house style 일관성이 크게 떨어졌습니다.

## 비용

실행 당시 Pollinations model registry 기준 flat image cost를 기준으로 계산한 역사적 기록입니다. provider 가격은 변경될 수 있으므로 현재 고정 단가로 간주하지 않습니다.

| Model | Cost/image | 이번 benchmark 32장 비용 |
| --- | ---: | ---: |
| flux | 0.002 Pollen | 0.064 Pollen |
| zimage | 0.004 Pollen | 0.128 Pollen |
| dreamshaper | 0.0001 Pollen | 0.0032 Pollen |

총 예상 비용은 약 `0.1952 Pollen`이었습니다.

## 해상도 결정

`1024x576`은 일부 선과 배경 디테일이 조금 더 정돈되지만 UCTale의 최대 표시 폭은 768 CSS px이고 품질 차이가 기본값 변경을 정당화할 정도로 크지 않았습니다.

Flux에서 1024x576은 768x432 대비 평균 latency 약 6.6%, 평균 응답 크기 약 53.7% 증가했습니다. Z-Image에서도 각각 약 13.0%, 64.0% 증가했습니다.

따라서 benchmark 시점의 model/resolution 결정은 다음과 같았습니다.

- `GAME_IMAGE_MODEL=flux`
- `GAME_IMAGE_WIDTH=768`
- `GAME_IMAGE_HEIGHT=432`

## style version에 대한 역사 기록

이 benchmark는 **`uctale-charcoal-v1` 시점의 model/resolution 비교**입니다. 따라서 당시 최종 결정에 v1이 기록되어 있었던 것은 맞으며, 이 문서의 96장 결과를 v2 결과로 재해석하지 않습니다.

이후 production smoke에서 핵폭발·불꽃 등 색채 의미가 강한 장면이 컬러 일러스트로 드리프트하는 사례가 발견되어 prompt style contract만 `uctale-charcoal-v2`로 강화되었습니다. 현재 production 기본값은 다음과 같습니다.

- model: `flux`
- resolution: `768x432`
- style: `uctale-charcoal-v2`

즉 **benchmark가 결정한 model/resolution은 유지되고, style version만 후속 운영 검증을 통해 v2로 진화**했습니다. 현재 계약은 `docs/architecture/image-generation.md`가 authoritative source입니다.

## 재현 방법

```bash
POLLINATIONS_TOKEN=... python scripts/benchmark_pollinations_images.py
```

출력:

- `build/pollinations-benchmark/images/`: review용 이미지
- `build/pollinations-benchmark/raw.csv`: fixture/model/size/seed/status/latency/MIME/bytes
- `build/pollinations-benchmark/summary.json`: model/size별 성공률, 평균·P95 지연

운영 secret과 생성 이미지는 저장소에 commit하지 않습니다. v2 style을 다시 정량/시각 비교하려면 기존 역사 결과를 덮어쓰지 않고 별도 output으로 재실행합니다.
