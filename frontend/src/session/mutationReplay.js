import { createResumeMeta } from './sessionRecovery.js'

const READY_META = Object.freeze({
  status: 'READY',
  statusMessage: '현재 완료 턴에서 계속할 수 있습니다.',
  canProgress: true,
})

export async function resolveMutationGame(response, resumeSession) {
  if (response?.replayed !== true) {
    return { game: response, meta: { ...READY_META } }
  }

  // 멱등성 replay는 과거의 불변 응답이다. 현재 플레이 가능 상태는 별도 GET만 신뢰한다.
  const current = await resumeSession(response.sessionId)
  if (
    !current?.game ||
    !Number.isInteger(response.turnNumber) ||
    !Number.isInteger(current.turnNumber) ||
    current.sessionId !== response.sessionId ||
    current.game.sessionId !== response.sessionId ||
    current.canonicalStateTurn !== current.turnNumber ||
    current.game.turnNumber !== current.turnNumber ||
    current.turnNumber < response.turnNumber
  ) {
    throw new Error('최신 저장 장면의 정합성을 확인하지 못했습니다. 다시 시도해 주세요.')
  }

  return { game: current.game, meta: createResumeMeta(current) }
}
