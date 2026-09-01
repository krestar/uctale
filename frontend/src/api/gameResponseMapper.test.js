import assert from 'node:assert/strict'
import test from 'node:test'

import { mapGameResponse } from './gameResponseMapper.js'

const statsFixture = [
  { key: 'MIGHT', score: 14, modifier: 2 },
  { key: 'AGILITY', score: 12, modifier: 1 },
  { key: 'INTELLECT', score: 10, modifier: 0 },
  { key: 'WILL', score: 8, modifier: -1 },
  { key: 'PRESENCE', score: 16, modifier: 3 },
]

const successFixture = {
  sessionId: 42,
  turnNumber: 2,
  title: '판정 장면',
  storyText: '문이 열렸다.',
  choices: [],
  mainImageUrl: null,
  characterStats: statsFixture,
  latestSkillCheck: {
    statType: 'WILL',
    rawRoll: 7,
    statModifier: 5,
    situationalModifier: -2,
    dc: 30,
    total: 1,
    outcome: 'SUCCESS',
    rulesetVersion: 1,
  },
}

test('모든 현재 능력치 key를 한국어 라벨로 매핑한다', () => {
  const mapped = mapGameResponse(successFixture)

  assert.deepEqual(
    mapped.rules.stats.items.map(({ key, label }) => [key, label]),
    [
      ['MIGHT', '근력'],
      ['AGILITY', '민첩'],
      ['INTELLECT', '지능'],
      ['WILL', '의지'],
      ['PRESENCE', '매력'],
    ],
  )
})

test('modifier와 outcome은 서버 값을 재계산하지 않고 그대로 표현한다', () => {
  const mapped = mapGameResponse(successFixture)

  assert.equal(mapped.rules.skillCheck.statModifierText, '+5')
  assert.equal(mapped.rules.skillCheck.situationalModifierText, '-2')
  assert.equal(mapped.rules.skillCheck.total, 1)
  assert.equal(mapped.rules.skillCheck.dc, 30)
  assert.equal(mapped.rules.skillCheck.outcome, 'SUCCESS')
  assert.equal(mapped.rules.skillCheck.outcomeLabel, '성공')
})

test('실패 outcome은 서버 FAILURE를 그대로 실패로 표현한다', () => {
  const mapped = mapGameResponse({
    ...successFixture,
    latestSkillCheck: {
      ...successFixture.latestSkillCheck,
      total: 99,
      dc: 1,
      outcome: 'FAILURE',
    },
  })

  assert.equal(mapped.rules.skillCheck.outcome, 'FAILURE')
  assert.equal(mapped.rules.skillCheck.outcomeLabel, '실패')
  assert.equal(mapped.rules.skillCheck.tone, 'failure')
})

test('새로운 enum key는 잘못 번역하지 않고 서버 key를 포함한 fallback으로 표시한다', () => {
  const mapped = mapGameResponse({
    ...successFixture,
    characterStats: [
      ...statsFixture,
      { key: 'FORTUNE', score: 11, modifier: 9 },
    ],
  })

  assert.equal(mapped.rules.stats.state, 'ready')
  assert.equal(mapped.rules.stats.items.at(-1).label, '알 수 없는 능력치 (FORTUNE)')
  assert.equal(mapped.rules.stats.items.at(-1).modifierText, '+9')
  assert.match(mapped.rules.stats.notice, /서버 키/)
})

test('필수 능력치가 누락되거나 중복되면 조용히 기본값으로 보정하지 않는다', () => {
  const missing = mapGameResponse({
    ...successFixture,
    characterStats: statsFixture.filter((stat) => stat.key !== 'WILL'),
  })
  const duplicate = mapGameResponse({
    ...successFixture,
    characterStats: [...statsFixture, statsFixture[0]],
  })

  assert.equal(missing.rules.stats.state, 'error')
  assert.equal(duplicate.rules.stats.state, 'error')
})

test('불완전한 Skill Check fixture는 판정값을 추측하지 않고 오류 상태로 남긴다', () => {
  const mapped = mapGameResponse({
    ...successFixture,
    latestSkillCheck: {
      statType: 'WILL',
      rawRoll: 10,
      outcome: 'SUCCESS',
    },
  })

  assert.equal(mapped.rules.skillCheck.state, 'error')
  assert.match(mapped.rules.skillCheck.message, /표시할 수 없습니다/)
})

test('opening에는 Skill Check가 없음을 명시적인 empty 상태로 표현한다', () => {
  const mapped = mapGameResponse({
    ...successFixture,
    turnNumber: 1,
    latestSkillCheck: null,
  })

  assert.equal(mapped.rules.skillCheck.state, 'empty')
})

test('동일 canonical 응답을 다시 매핑해도 표시 결과가 결정적으로 동일하다', () => {
  const first = mapGameResponse(successFixture)
  const replay = mapGameResponse(structuredClone(successFixture))

  assert.deepEqual(replay.rules, first.rules)
})
