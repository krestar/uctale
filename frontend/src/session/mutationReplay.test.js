import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveMutationGame } from './mutationReplay.js'

const past = { sessionId: 42, turnNumber: 2, storyText: '과거 장면', replayed: true }
function resumed(turn, status = 'READY', canProgress = true) {
  return {
    sessionId: 42,
    turnNumber: turn,
    canonicalStateTurn: turn,
    status,
    statusMessage: '현재 완료 턴',
    canProgress,
    game: { sessionId: 42, turnNumber: turn, storyText: '최신 장면' },
  }
}

test('신규 init/progress는 GET 없이 원본 canonical 응답을 표시한다', async () => {
  const normal = { ...past, replayed: false }
  const resolved = await resolveMutationGame(normal, () => {
    throw new Error('GET 호출 금지')
  })
  assert.equal(resolved.game, normal)
  assert.equal(resolved.meta.canProgress, true)
})

test('init/progress replay는 항상 세션 GET으로 최신 완료 턴을 사용한다', async () => {
  let reads = 0
  const resolve = () => resolveMutationGame(past, async (id) => {
    reads += 1
    assert.equal(id, 42)
    return resumed(4)
  })
  const init = await resolve()
  const progress = await resolve()
  assert.equal(reads, 2)
  assert.equal(init.game.turnNumber, 4)
  assert.equal(progress.game.turnNumber, 4)
  assert.equal(init.game.storyText, '최신 장면')
  assert.equal(progress.meta.canProgress, true)
  assert.equal(past.turnNumber, 2)
})

test('replay 이후에도 PROCESSING이면 최신 장면만 표시하고 다음 선택을 잠근다', async () => {
  const resolved = await resolveMutationGame(past, async () => resumed(4, 'PROCESSING', false))
  assert.equal(resolved.game.turnNumber, 4)
  assert.equal(resolved.meta.status, 'PROCESSING')
  assert.equal(resolved.meta.canProgress, false)
})

test('원본과 동일한 턴의 completed retry에서도 GET을 생략하지 않는다', async () => {
  const resolved = await resolveMutationGame(past, async () => resumed(2))
  assert.equal(resolved.game.turnNumber, 2)
  assert.equal(resolved.game.storyText, '최신 장면')
})

test('GET 실패·불완전 응답·세션 혼합·역행 턴은 과거 replay를 playable로 승인하지 않는다', async () => {
  await assert.rejects(resolveMutationGame(past, async () => { throw new Error('network') }), /network/)
  for (const result of [
    { ...resumed(4), game: null },
    { ...resumed(4), canonicalStateTurn: 3 },
    { ...resumed(4), game: { sessionId: 42, turnNumber: 3 } },
    { ...resumed(4), sessionId: 123 },
    { ...resumed(4), game: { sessionId: 123, turnNumber: 4 } },
    resumed(1),
  ]) {
    await assert.rejects(resolveMutationGame(past, async () => result), /정합성/)
  }
})
