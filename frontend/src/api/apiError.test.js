import test from 'node:test'
import assert from 'node:assert/strict'

import { getApiErrorMessage } from './apiError.js'

test('validation 오류는 서버의 안전한 사용자 메시지를 표시한다', () => {
  const error = { response: { data: { code: 'VALIDATION_ERROR', message: '세계관 설정은 255자 이하여야 합니다.' } } }
  assert.equal(getApiErrorMessage(error), '세계관 설정은 255자 이하여야 합니다.')
})

test('충돌 오류는 안정적인 사용자 메시지로 변환한다', () => {
  const error = { response: { data: { code: 'TURN_CONFLICT', message: 'internal detail' } } }
  assert.equal(
    getApiErrorMessage(error),
    '이미 처리되었거나 오래된 선택입니다. 최신 상태에서 다시 시도해주세요.'
  )
})

test('알 수 없는 서버 오류는 내부 메시지를 노출하지 않는다', () => {
  const error = { response: { data: { code: 'UNKNOWN', message: 'database internal detail' } } }
  assert.equal(getApiErrorMessage(error), '오류가 발생했습니다.')
})
