# Skill Check turn integration

## 목적

#37은 #7의 pure `SkillCheck` 규칙을 실제 `/progress` turn pipeline에 연결하는 첫 서버 주도 판정 vertical slice다.

핵심 원칙은 다음과 같다.

> roll, modifier, DC, total, success/failure는 서버가 한 번 확정하고, Narrative provider는 그 결과를 서술만 한다.

## 현재 action 정책

신규 typed choice는 서버가 `SKILL_CHECK` `AvailableAction`으로 발급한다.

현재 vertical slice의 최소 정책은 다음과 같다.

- stat: `WILL`
- DC: `10`
- situational modifier: `0`
- `choiceId`는 기존 choice 식별자와 일치해야 한다.

이 값은 provider가 결정하지 않는다. choice text는 Narrative provider가 제안할 수 있지만 action type과 rule arguments는 서버가 발급한다.

이 정책은 첫 end-to-end 판정 경계를 검증하기 위한 최소 규칙이며 최종 balancing이나 행동별 stat/DC 선택 시스템을 의미하지 않는다.

metadata가 없는 legacy wire 요청은 기존 `NARRATIVE_CHOICE` compatibility 의미를 유지한다.

## 판정 수명주기

1. mutation request가 `(session, expectedTurn)` reservation을 획득한다.
2. `ChoiceCodec`이 서버 발급 action 전체 metadata를 검증한다.
3. `TurnProcessor`가 `SKILL_CHECK` 여부를 확인한다.
4. `SkillCheckDecisionService`가 현재 reservation owner와 lease를 `FOR UPDATE`로 검증한다.
5. 같은 mutation request에 이미 저장된 판정이 있으면 재사용한다.
6. 없으면 production `SecureRandomSource`로 d20을 한 번 생성하고 판정 전체를 reservation에 저장한다.
7. `ActionResolver`가 저장된 판정이 action의 stat/DC/situational modifier와 현재 canonical stat modifier에 일치하는지 다시 검증한다.
8. `GameResult`와 canonical next state를 확정한다.
9. `NarrativeContext`에는 provider-safe `SkillCheckProjection`만 전달한다.
10. provider story가 유효하면 state transition과 동일 canonical commit에서 Skill Check audit을 `GameLog`에 기록한다.

## retry와 concurrency

### 같은 idempotency request

provider 실패나 DB 실패 뒤 같은 idempotency request가 재시작돼도 `game_turn_reservation.skill_check_request_id`가 같은 mutation request ID를 가리키면 저장된 판정을 재사용한다.

따라서 같은 사용자 mutation에서 provider retry나 canonical commit retry 때문에 d20이 바뀌지 않는다.

### 다른 request takeover

다른 mutation request가 만료된 lease를 takeover하면 이전 request의 provisional 판정을 canonical 결과로 재사용하지 않는다. 새 reservation owner/request가 새 판정을 확정해 기존 provisional 값을 교체한다.

이는 외부 provider exactly-once를 주장하는 것이 아니다. lease 만료 이후 takeover에서는 provider가 다시 호출될 수 있지만 stale owner는 canonical commit을 수행할 수 없다.

### provider attempt accounting

Skill Check 판정 생성은 provider attempt가 아니다. 기존 정책처럼 `provider_attempt_count`는 실제 Narrative provider 호출 직전에만 증가한다.

validation, rate limit, budget guard처럼 provider 호출 전 종료되는 경로가 provider attempt로 잘못 계산되지 않는 기존 경계를 유지한다.

## 감사 로그

Flyway V12는 `game_log`에 다음 nullable audit 필드를 추가한다.

- `skill_check_stat_type`
- `skill_check_raw_roll`
- `skill_check_stat_modifier`
- `skill_check_situational_modifier`
- `skill_check_dc`
- `skill_check_total`
- `skill_check_outcome`
- `skill_check_ruleset_version`

Skill Check가 없는 legacy/opening/narrative-only turn은 모두 `NULL`이다.

Skill Check가 있는 turn은 CHECK constraint로 전체 필드가 함께 존재해야 하므로 부분 audit이 조용히 저장되지 않는다.

## Narrative provider 경계

`NarrativeContext.SkillCheckProjection`은 다음 값을 제공한다.

- stat type
- raw roll
- stat modifier
- situational modifier
- DC
- total
- success/failure
- ruleset version

`PlayerAction.token`은 provider context에 포함하지 않는다.

Gemini prompt는 서버가 확정한 roll/modifier/DC/total/outcome을 변경하거나 재판정하지 않도록 명시한다. provider 응답은 canonical Skill Check 결과의 근거가 아니다.

## 테스트 경계

- `ActionResolverTest`: fixed `RandomSource` 성공/실패 경계, stale/invalid action, 저장 판정 재검증
- `GameServiceTest`: 같은 Skill Check 결과가 `NarrativeContext`와 canonical commit 후보에 전달되는지 검증
- `GeminiPromptContractTest`: raw roll부터 outcome까지 prompt projection 및 token 비노출 검증
- `PostgresSkillCheckDecisionTest`: 같은 mutation retry의 판정 재사용, 다른 takeover request의 새 판정 검증
- `PostgresSkillCheckAuditTest`: PostgreSQL `GameLog`에서 raw roll부터 outcome까지 조회 가능함을 검증
- 기존 M2 PostgreSQL matrix는 수정하지 않고 idempotency/concurrency/crash/stale-owner 회귀를 계속 검증한다.

## 범위 밖

- 전체 Combat
- natural 1/20 특수 규칙
- advantage/disadvantage
- 행동별 stat/DC 자동 선택 또는 balancing
- frontend 판정 상세 UI (#38)
