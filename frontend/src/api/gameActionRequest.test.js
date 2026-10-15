import assert from 'node:assert/strict'
import test from 'node:test'
import { buildProgressPayload } from './gameActionRequest.js'

test('전투와 수집 행동은 서버가 발급한 token·arguments를 그대로 전송한다', () => {
  const choice = {
    id: 7,
    text: '늑대를 공격한다',
    actionToken: 'issued-token',
    actionType: 'COMBAT_ATTACK',
    sourceTurn: 3,
    arguments: { encounterId: 'encounter:v2:1:wolf', targetEnemyId: 'enemy:wolf' },
  }
  const payload = buildProgressPayload(42, choice, 3)
  assert.deepEqual(payload, {
    sessionId: 42,
    choiceId: 7,
    expectedTurn: 3,
    actionToken: 'issued-token',
    actionType: 'COMBAT_ATTACK',
    sourceTurn: 3,
    arguments: choice.arguments,
  })
  assert.equal(payload.arguments, choice.arguments)
})

test('legacy 선택지는 새로운 gameplay action으로 자동 변환하지 않는다', () => {
  const payload = buildProgressPayload(42, { id: 3, text: '계속한다' }, 1)
  assert.equal(payload.actionType, null)
  assert.equal(payload.actionToken, null)
  assert.equal(payload.arguments, null)
})

test('불완전한 선택 입력은 전송 전에 거부한다', () => {
  assert.throws(() => buildProgressPayload(42, null, 1), TypeError)
  assert.throws(() => buildProgressPayload(42, { id: 1 }, Number.NaN), TypeError)
})
