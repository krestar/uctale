const STAT_LABELS = Object.freeze({
  MIGHT: '근력',
  AGILITY: '민첩',
  INTELLECT: '지능',
  WILL: '의지',
  PRESENCE: '매력',
})

const STAT_ORDER = Object.freeze(Object.keys(STAT_LABELS))
const OUTCOME_LABELS = Object.freeze({
  SUCCESS: '성공',
  FAILURE: '실패',
})

const formatSignedInteger = (value) => (value >= 0 ? `+${value}` : String(value))
const unknownStatLabel = (key) => `알 수 없는 능력치 (${key})`

function mapCharacterStats(characterStats) {
  if (!Array.isArray(characterStats) || characterStats.length === 0) {
    return {
      state: 'error',
      message: '캐릭터 능력치 정보를 표시할 수 없습니다.',
      items: [],
      notice: null,
    }
  }

  const byKey = new Map()
  for (const stat of characterStats) {
    if (
      !stat ||
      typeof stat.key !== 'string' ||
      stat.key.length === 0 ||
      !Number.isInteger(stat.score) ||
      !Number.isInteger(stat.modifier) ||
      byKey.has(stat.key)
    ) {
      return {
        state: 'error',
        message: '캐릭터 능력치 정보가 불완전합니다.',
        items: [],
        notice: null,
      }
    }
    byKey.set(stat.key, stat)
  }

  if (STAT_ORDER.some((key) => !byKey.has(key))) {
    return {
      state: 'error',
      message: '캐릭터 능력치 정보가 불완전합니다.',
      items: [],
      notice: null,
    }
  }

  const knownItems = STAT_ORDER.map((key) => {
    const stat = byKey.get(key)
    return {
      key,
      label: STAT_LABELS[key],
      score: stat.score,
      modifier: stat.modifier,
      modifierText: formatSignedInteger(stat.modifier),
      isUnknown: false,
    }
  })

  const unknownItems = characterStats
    .filter((stat) => !STAT_LABELS[stat.key])
    .map((stat) => ({
      key: stat.key,
      label: unknownStatLabel(stat.key),
      score: stat.score,
      modifier: stat.modifier,
      modifierText: formatSignedInteger(stat.modifier),
      isUnknown: true,
    }))

  return {
    state: 'ready',
    items: [...knownItems, ...unknownItems],
    notice: unknownItems.length > 0
      ? '새로운 능력치 이름의 한국어 번역이 없어 서버 키를 함께 표시합니다.'
      : null,
  }
}

function mapSkillCheck(latestSkillCheck, turnNumber) {
  if (latestSkillCheck == null) {
    return {
      state: 'empty',
      message: '아직 Skill Check 판정 기록이 없습니다.',
    }
  }

  const requiredIntegerFields = [
    'rawRoll',
    'statModifier',
    'situationalModifier',
    'dc',
    'total',
    'rulesetVersion',
  ]
  if (
    typeof latestSkillCheck !== 'object' ||
    typeof latestSkillCheck.statType !== 'string' ||
    latestSkillCheck.statType.length === 0 ||
    typeof latestSkillCheck.outcome !== 'string' ||
    latestSkillCheck.outcome.length === 0 ||
    requiredIntegerFields.some((field) => !Number.isInteger(latestSkillCheck[field]))
  ) {
    return {
      state: 'error',
      message: '최근 Skill Check 결과를 표시할 수 없습니다.',
    }
  }

  const statLabel = STAT_LABELS[latestSkillCheck.statType] ?? unknownStatLabel(latestSkillCheck.statType)
  const outcomeLabel = OUTCOME_LABELS[latestSkillCheck.outcome]
    ?? `알 수 없는 결과 (${latestSkillCheck.outcome})`
  const tone = latestSkillCheck.outcome === 'SUCCESS'
    ? 'success'
    : latestSkillCheck.outcome === 'FAILURE'
      ? 'failure'
      : 'unknown'

  return {
    state: 'ready',
    turnLabel: Number.isInteger(turnNumber) ? `턴 ${turnNumber}` : '최근 턴',
    statType: latestSkillCheck.statType,
    statLabel,
    rawRoll: latestSkillCheck.rawRoll,
    statModifier: latestSkillCheck.statModifier,
    statModifierText: formatSignedInteger(latestSkillCheck.statModifier),
    situationalModifier: latestSkillCheck.situationalModifier,
    situationalModifierText: formatSignedInteger(latestSkillCheck.situationalModifier),
    dc: latestSkillCheck.dc,
    total: latestSkillCheck.total,
    outcome: latestSkillCheck.outcome,
    outcomeLabel,
    tone,
    rulesetVersion: latestSkillCheck.rulesetVersion,
    notice: STAT_LABELS[latestSkillCheck.statType]
      ? null
      : '새로운 능력치 이름의 한국어 번역이 없어 서버 키를 함께 표시합니다.',
  }
}

export function mapGameResponse(response) {
  return {
    ...response,
    rules: {
      stats: mapCharacterStats(response?.characterStats),
      skillCheck: mapSkillCheck(response?.latestSkillCheck, response?.turnNumber),
    },
  }
}

export const gameResponsePresentation = {
  mapCharacterStats,
  mapSkillCheck,
}
