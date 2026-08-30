# Pollinations 이미지 benchmark

## 목적

UCTale 장면 삽화의 기본 model과 해상도를 감으로 바꾸지 않고 동일 prompt/seed 조건에서 비교하기 위한 절차다.

비교 대상:

- model: `flux`, `zimage`, `dreamshaper`
- resolution: `768x432`, `1024x576`
- fixture: 실내·실외, 단독 NPC, 다수 인물, 몬스터, 전투, 핵심 아이템, 장소 이동, 밝고 어두운 장면을 포함한 16개 장면
- seed: fixture별 고정 seed를 모든 model/resolution 조합에 동일 적용

## 실행 조건

2026-08-30에 실제 Pollinations 계정으로 benchmark를 실행했다.

- 총 요청: 96 (`16 fixtures × 3 models × 2 resolutions`)
- 성공: 96
- 실패: 0
- 모든 조합에 같은 fixture prompt와 seed를 사용했다.
- benchmark 결과 파일에는 token과 raw prompt를 기록하지 않았다.
- Python `urllib` 기본 User-Agent가 provider에서 403으로 차단되는 것을 실측으로 발견해 benchmark client에 명시적 User-Agent/Accept를 추가했고, 401/402/403은 fail-fast하도록 보완했다.

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

모델/해상도 이름을 숨긴 A~F 시트로 다시 섞어 prompt 준수도, charcoal style 일관성, 왜곡, 세부 묘사와 별도 재생성 없이 게임 삽화로 사용할 수 있는지를 비교했다.

### flux

- UCTale의 `rough charcoal sketch`, 흑백, 거친 종이 질감과 story concept art 방향을 가장 안정적으로 유지했다.
- 전투, 몬스터, 다수 인물과 판타지 장면에서 focal point와 silhouette가 비교적 명확했다.
- 현실 장면 일부는 zimage보다 literal한 장소 표현이 약한 경우가 있었지만, 전체적인 house style과 장면 가독성의 균형이 가장 좋았다.

### zimage

- 흑백 sketch 스타일과 공간 묘사가 안정적이고 일상/실내 장면의 prompt 준수도도 좋았다.
- 다만 flux 대비 평균 지연 시간이 길고 현재 단가도 더 높으며, 판타지/액션 fixture에서 기본 모델을 교체할 만큼 일관된 품질 우위는 확인되지 않았다.
- 향후 대체 후보 또는 특정 장면용 후보로는 유지할 가치가 있다.

### dreamshaper

- 세 모델 중 가장 빠르고 응답 크기도 작았다.
- 그러나 요청한 charcoal sketch보다 어두운 cinematic/photorealistic 이미지로 치우치는 경우가 반복되어 UCTale house style 일관성이 크게 떨어졌다.
- 가격과 속도만으로 기본 모델을 바꾸기에는 품질 방향이 맞지 않아 기본 후보에서 제외한다.

## 비용

실행 당시 Pollinations model registry 기준 flat image cost를 기준으로 계산한다.

| Model | Cost/image | 이번 benchmark 32장 비용 |
| --- | ---: | ---: |
| flux | 0.002 Pollen | 0.064 Pollen |
| zimage | 0.004 Pollen | 0.128 Pollen |
| dreamshaper | 0.0001 Pollen | 0.0032 Pollen |

총 예상 비용은 약 `0.1952 Pollen`이다. 실제 운영에서는 Pollinations account usage를 함께 확인하며 가격은 provider 변경 가능성이 있으므로 고정 상수로 간주하지 않는다.

사용 가능한 이미지 한 장당 비용은 단순 최저 단가보다 house style 적합성을 포함해 판단한다. DreamShaper는 명목 단가는 가장 낮지만 charcoal style 불일치 때문에 UCTale 기준 usable 후보로 보기 어렵고, Z-Image는 Flux보다 높은 단가와 지연을 상쇄할 만큼 품질 우위가 확인되지 않았다. 따라서 현재 운영 선택에서는 Flux의 비용 대비 usable 품질이 가장 낫다고 판단한다.

## 해상도 결정

`1024x576`은 일부 선과 배경 디테일이 조금 더 정돈되지만, UCTale의 현재 최대 표시 폭은 768 CSS px이고 품질 차이가 기본값 변경을 정당화할 정도로 크지 않았다.

특히 Flux에서 1024x576은 768x432 대비:

- 평균 latency 약 6.6% 증가
- 평균 응답 크기 약 53.7% 증가

Z-Image에서도 1024x576은:

- 평균 latency 약 13.0% 증가
- 평균 응답 크기 약 64.0% 증가

따라서 네트워크/응답 크기 비용을 늘리면서 기본 해상도를 올릴 근거가 부족하다.

## 최종 결정

production 기본값을 변경하지 않는다.

- `GAME_IMAGE_MODEL=flux`
- `GAME_IMAGE_WIDTH=768`
- `GAME_IMAGE_HEIGHT=432`
- `GAME_IMAGE_STYLE_VERSION=uctale-charcoal-v1`

결론은 "기존 값이므로 유지"가 아니라, 실제 96장 benchmark에서 `flux + 768x432`가 품질, style 일관성, latency, 응답 크기와 비용의 균형이 가장 좋았기 때문에 유지한다는 것이다.

## 재현 방법

```bash
POLLINATIONS_TOKEN=... python scripts/benchmark_pollinations_images.py
```

출력:

- `build/pollinations-benchmark/images/`: review용 이미지
- `build/pollinations-benchmark/raw.csv`: fixture/model/size/seed/status/latency/MIME/bytes
- `build/pollinations-benchmark/summary.json`: model/size별 성공률, 평균·P95 지연

운영 secret과 생성 이미지는 저장소에 commit하지 않는다.
