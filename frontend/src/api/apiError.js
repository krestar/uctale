const ERROR_MESSAGES = {
  SESSION_NOT_FOUND: '게임 세션을 찾을 수 없습니다.',
  TURN_CONFLICT: '이미 처리되었거나 오래된 선택입니다. 최신 상태에서 다시 시도해주세요.',
  INVALID_CHOICE: '현재 턴에서 선택할 수 없는 선택지입니다.',
  PROVIDER_RESPONSE_INVALID: '이야기 생성 응답이 올바르지 않습니다. 다시 시도해주세요.',
  PERSISTENCE_FAILURE: '게임 상태 저장 중 오류가 발생했습니다. 다시 시도해주세요.',
}

export const getApiErrorMessage = (error, fallback = '오류가 발생했습니다.') => {
  const code = error?.response?.data?.code
  if (code === 'VALIDATION_ERROR' || code === 'INVALID_REQUEST') {
    return error?.response?.data?.message || fallback
  }
  return ERROR_MESSAGES[code] || fallback
}
