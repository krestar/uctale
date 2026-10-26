import { useEffect, useRef, useState } from 'react'
import {
  checkAccessSession,
  createIdempotencyKey,
  initGame,
  listGameSessions,
  progressGame,
  resolveGameAssetUrl,
  resumeGameSession,
  verifyPassword,
} from './api/gameApi'
import { getApiErrorCode, getApiErrorMessage, isAccessAuthError } from './api/apiError'
import { createResumeMeta } from './session/sessionRecovery.js'
import { resolveMutationGame } from './session/mutationReplay.js'
import { createAsyncRequestGate, progressRequestKey } from './session/asyncRequestGate.js'
import AccessScreen from './screens/AccessScreen'
import GamePlayScreen from './screens/GamePlayScreen'
import GameSetupScreen from './screens/GameSetupScreen'
import './App.css'
import './interaction.css'

const RETRYABLE_PROGRESS_ERROR_CODES = new Set([
  'PROVIDER_RESPONSE_INVALID',
  'PERSISTENCE_FAILURE',
  'RATE_LIMIT_EXCEEDED',
])

function isRetryableProgressError(error) {
  if (!error?.response) return true
  const code = getApiErrorCode(error)
  if (code === 'NARRATIVE_PROVIDER_RECOVERY_WAIT') return false
  if (error.response.status >= 500) return true
  return RETRYABLE_PROGRESS_ERROR_CODES.has(code)
}

function App() {
  const [world, setWorld] = useState('')
  const [character, setCharacter] = useState('')
  const [gameData, setGameData] = useState(null)
  const [resumeMeta, setResumeMeta] = useState(null)
  const [isTypingComplete, setIsTypingComplete] = useState(false)
  const initIdempotencyKeyRef = useRef(null)

  const [authState, setAuthState] = useState('checking')
  const [password, setPassword] = useState('')
  const [authMessage, setAuthMessage] = useState('')
  const [authMessageKind, setAuthMessageKind] = useState('api')
  const [isAuthLoading, setIsAuthLoading] = useState(false)

  const [sessions, setSessions] = useState([])
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [sessionsError, setSessionsError] = useState('')
  const [sessionsNextCursor, setSessionsNextCursor] = useState(null)
  const [sessionsHasMore, setSessionsHasMore] = useState(false)
  const sessionRequestRef = useRef(0)
  const requestGateRef = useRef(createAsyncRequestGate())
  const [resumingSessionId, setResumingSessionId] = useState(null)

  const [setupFieldErrors, setSetupFieldErrors] = useState({})
  const [setupError, setSetupError] = useState('')
  const [isStarting, setIsStarting] = useState(false)

  const [progressError, setProgressError] = useState(null)
  const [pendingChoiceId, setPendingChoiceId] = useState(null)
  const [isProgressing, setIsProgressing] = useState(false)

  const sessionId = gameData?.sessionId
  const turnNumber = gameData?.turnNumber
  const mainImageUrl = resolveGameAssetUrl(gameData?.mainImageUrl)

  const requireReauthentication = (error) => {
    if (!isAccessAuthError(error)) return false
    setAuthMessage(getApiErrorMessage(error))
    setAuthMessageKind('api')
    requestGateRef.current.invalidate()
    sessionRequestRef.current += 1
    setSessionsLoading(false)
    setResumingSessionId(null)
    setIsProgressing(false)
    setIsStarting(false)
    setAuthState('login')
    setGameData(null)
    setResumeMeta(null)
    return true
  }

  const loadSessions = async (append = false) => {
    if (append && (!sessionsHasMore || sessionsLoading)) return
    const request = ++sessionRequestRef.current
    const epoch = requestGateRef.current.epoch()
    const cursor = append ? sessionsNextCursor : null
    setSessionsLoading(true)
    setSessionsError('')
    try {
      const data = await listGameSessions(cursor)
      if (request !== sessionRequestRef.current || !requestGateRef.current.isCurrent(epoch)) return
      setSessions((current) => append
        ? [...current, ...data.items.filter((item) => !current.some((existing) => existing.sessionId === item.sessionId))]
        : data.items)
      setSessionsNextCursor(data.nextCursor)
      setSessionsHasMore(data.hasMore)
    } catch (error) {
      if (request !== sessionRequestRef.current) return
      if (!requireReauthentication(error)) {
        setSessionsError(getApiErrorMessage(error, '저장된 이야기를 불러오지 못했습니다.'))
      }
    } finally {
      if (request === sessionRequestRef.current && requestGateRef.current.isCurrent(epoch)) setSessionsLoading(false)
    }
  }

  useEffect(() => {
    checkAccessSession()
      .then(() => {
        setAuthState('authenticated')
        return loadSessions()
      })
      .catch(() => setAuthState('login'))
  // Access session is checked only on initial mount.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleLogin = async () => {
    if (isAuthLoading) return

    if (!password) {
      setAuthMessage('비밀번호를 입력해주세요.')
      setAuthMessageKind('validation')
      return
    }

    setIsAuthLoading(true)
    setAuthMessage('')

    try {
      await verifyPassword(password)
      setPassword('')
      setAuthMessage('')
      setAuthState('authenticated')
      await loadSessions()
    } catch (error) {
      setAuthMessage(getApiErrorMessage(error, '로그인에 실패했습니다.'))
      setAuthMessageKind('api')
    } finally {
      setIsAuthLoading(false)
    }
  }

  const clearSetupFieldError = (field) => {
    setSetupFieldErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
    setSetupError('')
  }

  const handleWorldChange = (value) => {
    setWorld(value)
    initIdempotencyKeyRef.current = null
    clearSetupFieldError('world')
  }

  const handleCharacterChange = (value) => {
    setCharacter(value)
    initIdempotencyKeyRef.current = null
    clearSetupFieldError('character')
  }

  const validateSetup = () => {
    const nextErrors = {}

    if (!world.trim()) nextErrors.world = '세계관을 입력해주세요.'
    if (!character.trim()) nextErrors.character = '캐릭터 설정을 입력해주세요.'

    setSetupFieldErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  const handleStartGame = async () => {
    if (isStarting || !validateSetup()) return

    const idempotencyKey = initIdempotencyKeyRef.current || createIdempotencyKey()
    initIdempotencyKeyRef.current = idempotencyKey
    const epoch = requestGateRef.current.epoch()
    setIsStarting(true)
    setSetupError('')

    try {
      const data = await initGame(world, character, idempotencyKey)
      const resolved = await resolveMutationGame(data, resumeGameSession)
      if (!requestGateRef.current.isCurrent(epoch)) return
      initIdempotencyKeyRef.current = null
      setGameData(resolved.game)
      setResumeMeta(resolved.meta)
      setIsTypingComplete(false)
      setProgressError(null)
      setPendingChoiceId(null)
    } catch (error) {
      if (!requestGateRef.current.isCurrent(epoch)) return
      if (!requireReauthentication(error)) {
        console.error(error)
        setSetupError(getApiErrorMessage(
          error,
          '스토리를 시작하지 못했습니다. 연결 상태를 확인하고 다시 시도해주세요.',
        ))
      }
    } finally {
      if (requestGateRef.current.isCurrent(epoch)) setIsStarting(false)
    }
  }

  const handleResume = async (targetSessionId) => {
    const epoch = requestGateRef.current.begin('resume')
    if (epoch == null) return
    setResumingSessionId(targetSessionId)
    setSessionsError('')
    try {
      const resumed = await resumeGameSession(targetSessionId)
      if (!requestGateRef.current.isCurrent(epoch)) return
      if (!resumed.game) {
        setSessionsError(resumed.statusMessage || '이 세션은 안전하게 재개할 수 없습니다.')
        await loadSessions()
        return
      }
      setGameData(resumed.game)
      setResumeMeta(createResumeMeta(resumed))
      setIsTypingComplete(false)
      setProgressError(null)
      setPendingChoiceId(null)
    } catch (error) {
      if (!requestGateRef.current.isCurrent(epoch)) return
      if (!requireReauthentication(error)) {
        setSessionsError(getApiErrorMessage(error, '세션을 재개하지 못했습니다.'))
      }
    } finally {
      requestGateRef.current.end('resume', epoch)
      if (requestGateRef.current.isCurrent(epoch)) setResumingSessionId(null)
    }
  }

  const handleChoice = async (choiceId, retryIdempotencyKey = null) => {
    if (!sessionId || turnNumber == null || isProgressing || !isTypingComplete || resumeMeta?.canProgress === false) return

    const choice = gameData?.choices?.find((candidate) => candidate.id === choiceId)
    if (!choice) return

    const epoch = requestGateRef.current.begin('progress')
    if (epoch == null) return
    const idempotencyKey = progressRequestKey(retryIdempotencyKey, createIdempotencyKey)
    setIsProgressing(true)
    setPendingChoiceId(choiceId)
    setProgressError(null)

    try {
      const nextData = await progressGame(sessionId, choice, turnNumber, idempotencyKey)
      const resolved = await resolveMutationGame(nextData, resumeGameSession)
      if (!requestGateRef.current.isCurrent(epoch)) return
      setGameData(resolved.game)
      setResumeMeta(resolved.meta)
      setIsTypingComplete(false)
      setProgressError(null)
    } catch (error) {
      if (!requestGateRef.current.isCurrent(epoch)) return
      if (!requireReauthentication(error)) {
        console.error(error)
        const errorCode = getApiErrorCode(error)
        let resumedMeta = null
        if (isRetryableProgressError(error) || errorCode === 'NARRATIVE_PROVIDER_RECOVERY_WAIT') {
          try {
            const resumed = await resumeGameSession(sessionId)
            if (!requestGateRef.current.isCurrent(epoch)) return
            if (resumed?.game) {
              resumedMeta = createResumeMeta(resumed)
              setResumeMeta(resumedMeta)
              // 전송 실패 직후 서버 commit이 성공했다면 완료 턴을 다시 반영한다.
              // 같은 선택을 새 key로 재실행하거나 오래된 expectedTurn을 사용하지 않는다.
              if (resumed.sessionId === sessionId &&
                  resumed.canonicalStateTurn === resumed.turnNumber &&
                  resumed.game.turnNumber === resumed.turnNumber &&
                  resumed.game.turnNumber > turnNumber) {
                setGameData(resumed.game)
                setIsTypingComplete(false)
                setProgressError(null)
                return
              }
            }
          } catch (resumeError) {
            if (!requestGateRef.current.isCurrent(epoch)) return
            if (!requireReauthentication(resumeError)) {
              console.error(resumeError)
            }
          }
        }
        setProgressError({
          choiceId,
          choiceText: choice.text,
          idempotencyKey,
          message: getApiErrorMessage(
            error,
            '선택을 진행하지 못했습니다. 연결 상태를 확인하고 다시 시도해주세요.',
          ),
          canRetry: isRetryableProgressError(error) && resumedMeta?.canProgress !== false,
        })
      }
    } finally {
      requestGateRef.current.end('progress', epoch)
      if (requestGateRef.current.isCurrent(epoch)) {
        setPendingChoiceId(null)
        setIsProgressing(false)
      }
    }
  }

  const handleRetryChoice = () => {
    if (!progressError?.canRetry) return
    handleChoice(progressError.choiceId, progressError.idempotencyKey)
  }

  const handleReturnToStart = () => {
    if (isProgressing) return
    initIdempotencyKeyRef.current = null
    setGameData(null)
    setResumeMeta(null)
    setIsTypingComplete(false)
    setProgressError(null)
    setPendingChoiceId(null)
    loadSessions()
  }

  if (authState !== 'authenticated') {
    return (
      <AccessScreen
        authState={authState}
        password={password}
        authMessage={authMessage}
        authMessageKind={authMessageKind}
        isLoading={isAuthLoading}
        onPasswordChange={(value) => {
          setPassword(value)
          if (authMessageKind === 'validation') setAuthMessage('')
        }}
        onLogin={handleLogin}
      />
    )
  }

  if (gameData) {
    return (
      <GamePlayScreen
        gameData={gameData}
        mainImageUrl={mainImageUrl}
        isProgressing={isProgressing}
        isTypingComplete={isTypingComplete}
        pendingChoiceId={pendingChoiceId}
        progressError={progressError}
        resumeMeta={resumeMeta}
        onTypingComplete={() => setIsTypingComplete(true)}
        onChoice={handleChoice}
        onRetryChoice={handleRetryChoice}
        onReturnToStart={handleReturnToStart}
        onAuthError={requireReauthentication}
      />
    )
  }

  return (
    <GameSetupScreen
      world={world}
      character={character}
      fieldErrors={setupFieldErrors}
      requestError={setupError}
      isLoading={isStarting}
      sessions={sessions}
      sessionsLoading={sessionsLoading}
      sessionsError={sessionsError}
      sessionsHasMore={sessionsHasMore}
      onLoadMoreSessions={() => loadSessions(true)}
      resumingSessionId={resumingSessionId}
      onReloadSessions={() => loadSessions(false)}
      onResumeSession={handleResume}
      onAuthError={requireReauthentication}
      onWorldChange={handleWorldChange}
      onCharacterChange={handleCharacterChange}
      onStart={handleStartGame}
      onRetry={handleStartGame}
    />
  )
}

export default App
