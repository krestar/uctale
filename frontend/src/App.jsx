import { useEffect, useState } from 'react'
import { checkAccessSession, initGame, progressGame, resolveGameAssetUrl, verifyPassword } from './api/gameApi'
import { getApiErrorCode, getApiErrorMessage, isAccessAuthError } from './api/apiError'
import AccessScreen from './screens/AccessScreen'
import GamePlayScreen from './screens/GamePlayScreen'
import GameSetupScreen from './screens/GameSetupScreen'
import './App.css'

const RETRYABLE_PROGRESS_ERROR_CODES = new Set([
  'PROVIDER_RESPONSE_INVALID',
  'PERSISTENCE_FAILURE',
  'RATE_LIMIT_EXCEEDED',
])

function isRetryableProgressError(error) {
  if (!error?.response) return true
  if (error.response.status >= 500) return true
  return RETRYABLE_PROGRESS_ERROR_CODES.has(getApiErrorCode(error))
}

function App() {
  const [world, setWorld] = useState('')
  const [character, setCharacter] = useState('')
  const [gameData, setGameData] = useState(null)
  const [isTypingComplete, setIsTypingComplete] = useState(false)

  const [authState, setAuthState] = useState('checking')
  const [password, setPassword] = useState('')
  const [authMessage, setAuthMessage] = useState('')
  const [authMessageKind, setAuthMessageKind] = useState('api')
  const [isAuthLoading, setIsAuthLoading] = useState(false)

  const [setupFieldErrors, setSetupFieldErrors] = useState({})
  const [setupError, setSetupError] = useState('')
  const [isStarting, setIsStarting] = useState(false)

  const [progressError, setProgressError] = useState(null)
  const [pendingChoiceId, setPendingChoiceId] = useState(null)
  const [isProgressing, setIsProgressing] = useState(false)

  const sessionId = gameData?.sessionId
  const turnNumber = gameData?.turnNumber
  const mainImageUrl = resolveGameAssetUrl(gameData?.mainImageUrl)

  useEffect(() => {
    checkAccessSession()
      .then(() => setAuthState('authenticated'))
      .catch(() => setAuthState('login'))
  }, [])

  const requireReauthentication = (error) => {
    if (!isAccessAuthError(error)) return false
    setAuthMessage(getApiErrorMessage(error))
    setAuthMessageKind('api')
    setAuthState('login')
    return true
  }

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
    clearSetupFieldError('world')
  }

  const handleCharacterChange = (value) => {
    setCharacter(value)
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

    setIsStarting(true)
    setSetupError('')

    try {
      const data = await initGame(world, character)
      setGameData(data)
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

  const handleChoice = async (choiceId) => {
    if (!sessionId || turnNumber == null || isProgressing || !isTypingComplete) return

    const choice = gameData?.choices?.find((candidate) => candidate.id === choiceId)
    if (!choice) return

    setIsProgressing(true)
    setPendingChoiceId(choiceId)
    setProgressError(null)

    try {
      const nextData = await progressGame(sessionId, choiceId, turnNumber)
      setGameData(nextData)
      setIsTypingComplete(false)
      setProgressError(null)
    } catch (error) {
      if (!requireReauthentication(error)) {
        console.error(error)
        setProgressError({
          choiceId,
          choiceText: choice.text,
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
    handleChoice(progressError.choiceId)
  }

  const handleReturnToStart = () => {
    if (isProgressing) return
    setGameData(null)
    setIsTypingComplete(false)
    setProgressError(null)
    setPendingChoiceId(null)
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
      onWorldChange={handleWorldChange}
      onCharacterChange={handleCharacterChange}
      onStart={handleStartGame}
      onRetry={handleStartGame}
    />
  )
}

export default App
