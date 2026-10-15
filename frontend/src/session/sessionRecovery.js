export const SESSION_STATUS_LABELS = Object.freeze({
  READY: '계속할 수 있음',
  PROCESSING: '선택 처리 중',
  RECOVERY_WAIT: '다시 시도 대기',
  FAILED: '이어서 진행 가능',
  UNRECOVERABLE: '이어갈 수 없음',
})

export const SESSION_STATUS_HELP = Object.freeze({
  READY: '마지막으로 저장된 장면부터 이어갈 수 있습니다.',
  PROCESSING: '선택한 행동을 처리하고 있습니다. 마지막 장면은 안전하게 저장되어 있습니다.',
  RECOVERY_WAIT: '이야기 생성이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.',
  FAILED: '이전 요청이 끝나지 않았습니다. 마지막 장면부터 다시 선택할 수 있습니다.',
  UNRECOVERABLE: '저장된 이야기에 문제가 있어 현재는 이어갈 수 없습니다.',
})

export function createResumeMeta(resumed) {
  return {
    status: resumed?.status ?? 'UNRECOVERABLE',
    statusMessage: SESSION_STATUS_HELP[resumed?.status] ?? '현재 이야기를 이어갈 수 없습니다.',
    retryable: resumed?.retryable === true,
    canProgress: resumed?.canProgress === true,
    retryAfterSeconds: Number.isFinite(resumed?.retryAfterSeconds) ? resumed.retryAfterSeconds : null,
  }
}

export function canOpenSession(session) {
  return session?.canResume === true && session?.status !== 'UNRECOVERABLE'
}
