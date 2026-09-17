export const SESSION_STATUS_LABELS = Object.freeze({
  READY: '계속할 수 있음',
  PROCESSING: '진행 처리 중',
  FAILED: '복구 필요',
  UNRECOVERABLE: '복구 불가',
})

export function createResumeMeta(resumed) {
  return {
    status: resumed?.status ?? 'UNRECOVERABLE',
    statusMessage: resumed?.statusMessage ?? '이 세션은 안전하게 재개할 수 없습니다.',
    retryable: resumed?.retryable === true,
    canProgress: resumed?.canProgress === true,
  }
}

export function canOpenSession(session) {
  return session?.canResume === true && session?.status !== 'UNRECOVERABLE'
}
