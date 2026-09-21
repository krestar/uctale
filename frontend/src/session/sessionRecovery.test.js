import assert from 'node:assert/strict'
import test from 'node:test'

import { canOpenSession, createResumeMeta } from './sessionRecovery.js'

test('PROCESSING resume는 마지막 완료 턴을 보여도 새 진행을 잠근다', () => {
  const meta = createResumeMeta({
    status: 'PROCESSING',
    statusMessage: '처리 중',
    retryable: false,
    canProgress: false,
    game: { turnNumber: 4 },
  })

  assert.equal(meta.status, 'PROCESSING')
  assert.equal(meta.canProgress, false)
  assert.equal(meta.retryable, false)
})

test('RECOVERY_WAIT resume는 retryable이지만 cooldown 동안 진행을 잠근다', () => {
  const meta = createResumeMeta({
    status: 'RECOVERY_WAIT',
    statusMessage: 'provider 복구 대기',
    retryable: true,
    canProgress: false,
    retryAfterSeconds: 30,
  })

  assert.equal(meta.status, 'RECOVERY_WAIT')
  assert.equal(meta.canProgress, false)
  assert.equal(meta.retryable, true)
  assert.equal(meta.retryAfterSeconds, 30)
})

test('retryable FAILED resume는 마지막 완료 턴에서 다시 진행할 수 있다', () => {
  const meta = createResumeMeta({
    status: 'FAILED',
    statusMessage: '이전 요청 실패',
    retryable: true,
    canProgress: true,
  })

  assert.equal(meta.status, 'FAILED')
  assert.equal(meta.canProgress, true)
  assert.equal(meta.retryable, true)
})

test('UNRECOVERABLE summary는 재개 대상으로 열지 않는다', () => {
  assert.equal(canOpenSession({ status: 'UNRECOVERABLE', canResume: false }), false)
  assert.equal(canOpenSession({ status: 'READY', canResume: true }), true)
})
