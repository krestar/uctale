/**
 * The client only echoes the action contract issued by the server.
 * No gameplay intent, numerical consequence, or action token is synthesized here.
 */
export function buildProgressPayload(sessionId, choice, expectedTurn) {
  if (!choice || !Number.isInteger(choice.id) || !Number.isInteger(expectedTurn)) {
    throw new TypeError('유효한 선택지와 장면 번호가 필요합니다.')
  }
  return {
    sessionId,
    choiceId: choice.id,
    expectedTurn,
    actionToken: choice.actionToken ?? null,
    actionType: choice.actionType ?? null,
    sourceTurn: choice.sourceTurn ?? null,
    arguments: choice.arguments ?? null,
  }
}
