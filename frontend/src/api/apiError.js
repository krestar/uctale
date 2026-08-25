const ERROR_MESSAGES = {
  SESSION_NOT_FOUND: '게임 세션을 찾을 수 없습니다.',
  TURN_CONFLICT: '이미 처리되었거나 오래된 선택입니다. 최신 상태에서 다시 시도해주세요.',
  INVALID_CHOICE: '현재 턴에서 선택할 수 없는 선택지입니다.',
  PROVIDER_RESPONSE_INVALID: '이야기 생성 응답이 올바르지 않습니다. 다시 시도해주세요.',
  PERSISTENCE_FAILURE: '게임 상태 저장 중 오류가 발생했습니다. 다시 시도해주세요.',
  INVALID_CREDENTIALS: '비밀번호가 올바르지 않습니다.',
  ACCESS_SESSION_REQUIRED: '접근 인증이 필요합니다.',
  ACCESS_SESSION_INVALID: '접근 세션이 올바르지 않습니다. 다시 로그인해주세요.',
  ACCESS_SESSION_EXPIRED: '접근 세션이 만료되었습니다. 다시 로그인해주세요.',
}

const AUTH_ERROR_CODES = new Set([
  'INVALID_CREDENTIALS',
  'ACCESS_SESSION_REQUIRED',
  'ACCESS_SESSION_INVALID',
  'ACCESS_SESSION_EXPIRED',
])

export const getApiErrorCode = (error) => error?.response?.data?.code

export const isAccessAuthError = (error) => AUTH_ERROR_CODES.has(getApiErrorCode(error))

export const getApiErrorMessage = (error, fallback = '오류가 발생했습니다.') => {
  const code = getApiErrorCode(error)
  if (code === 'VALIDATION_ERROR' || code === 'INVALID_REQUEST') {
    return error?.response?.data?.message || fallback
  }
  return ERROR_MESSAGES[code] || fallback
}
