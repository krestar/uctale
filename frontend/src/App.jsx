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
    setAuthState('login')
    setGameData(null)
    setResumeMeta(null)
    return true
  }

  const loadSessions = async () => {
    setSessionsLoading(true)
    setSessionsError('')
    try {
      const data = await listGameSessions()
      setSessions(Array.isArray(data) ? data : [])
    } catch (error) {
      if (!requireReauthentication(error)) {
        setSessionsError(getApiErrorMessage(error, '저장된 이야기를 불러오지 못했습니다.'))
      }
    } finally {
      setSessionsLoading(false)
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
    setIsStarting(true)
    setSetupError('')

    try {
      const data = await initGame(world, character, idempotencyKey)
      initIdempotencyKeyRef.current = null
      setGameData(data)
      setResumeMeta({ status: 'READY', statusMessage: '현재 완료 턴에서 계속할 수 있습니다.', canProgress: true })
      setIsTypingComplete(false)
      setProgressError(null)
      setPendingChoiceId(null)
    } catch (error) {
      if (!requireReauthentication(error)) {
        console.error(error)
        setSetupError(getApiErrorMessage(
          error,
          '스토리를 시작하지 못했습니다. 연결 상태를 확인하고 다시 시도해주세요.',
        ))
      }
    } finally {
      setIsStarting(false)
    }
  }

  const handleResume = async (targetSessionId) => {
    if (resumingSessionId != null) return
    setResumingSessionId(targetSessionId)
    setSessionsError('')
    try {
      const resumed = await resumeGameSession(targetSessionId)
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
      if (!requireReauthentication(error)) {
        setSessionsError(getApiErrorMessage(error, '세션을 재개하지 못했습니다.'))
      }
    } finally {
      setResumingSessionId(null)
    }
  }

  const handleChoice = async (choiceId, retryIdempotencyKey = null) => {
    if (!sessionId || turnNumber == null || isProgressing || !isTypingComplete || resumeMeta?.canProgress === false) return

    const choice = gameData?.choices?.find((candidate) => candidate.id === choiceId)
    if (!choice) return

    const idempotencyKey = retryIdempotencyKey || createIdempotencyKey()
    setIsProgressing(true)
    setPendingChoiceId(choiceId)
    setProgressError(null)

    try {
      const nextData = await progressGame(sessionId, choice, turnNumber, idempotencyKey)
      setGameData(nextData)
      setResumeMeta({ status: 'READY', statusMessage: '현재 완료 턴에서 계속할 수 있습니다.', canProgress: true })
      setIsTypingComplete(false)
      setProgressError(null)
    } catch (error) {
      if (!requireReauthentication(error)) {
        console.error(error)
        const errorCode = getApiErrorCode(error)
        if (errorCode === 'NARRATIVE_PROVIDER_RECOVERY_WAIT') {
          try {
            const resumed = await resumeGameSession(sessionId)
            if (resumed?.game) {
              setResumeMeta(createResumeMeta(resumed))
            }
          } catch (resumeError) {
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
          canRetry: isRetryableProgressError(error),
        })
      }
    } finally {
      setPendingChoiceId(null)
      setIsProgressing(false)
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
      resumingSessionId={resumingSessionId}
      onReloadSessions={loadSessions}
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
