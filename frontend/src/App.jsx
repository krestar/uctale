import { useEffect, useState } from 'react'
import { checkAccessSession, initGame, progressGame, resolveGameAssetUrl, verifyPassword } from './api/gameApi'
import { getApiErrorMessage, isAccessAuthError } from './api/apiError'
import AccessScreen from './screens/AccessScreen'
import GamePlayScreen from './screens/GamePlayScreen'
import GameSetupScreen from './screens/GameSetupScreen'
import './App.css'

function App() {
  const [world, setWorld] = useState('')
  const [character, setCharacter] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [gameData, setGameData] = useState(null)
  const [isTypingComplete, setIsTypingComplete] = useState(false)
  const [authState, setAuthState] = useState('checking')
  const [password, setPassword] = useState('')
  const [authMessage, setAuthMessage] = useState('')

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
    setAuthState('login')
    return true
  }

  const handleLogin = async () => {
    if (!password) {
      setAuthMessage('비밀번호를 입력해주세요.')
      return
    }

    setIsLoading(true)
    setAuthMessage('')

    try {
      await verifyPassword(password)
      setPassword('')
      setAuthState('authenticated')
    } catch (error) {
      setAuthMessage(getApiErrorMessage(error, '로그인에 실패했습니다.'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleStartGame = async () => {
    if (!world || !character) {
      alert('세계관과 캐릭터 설정을 모두 입력해주세요!')
      return
    }

    setIsLoading(true)

    try {
      const data = await initGame(world, character)
      setGameData(data)
      setIsTypingComplete(false)
    } catch (error) {
      if (!requireReauthentication(error)) {
        console.error(error)
        alert(getApiErrorMessage(error))
      }
    } finally {
      setIsLoading(false)
    }
  }

  const handleChoice = async (choiceId) => {
    if (!sessionId || !turnNumber) return

    setIsLoading(true)

    try {
      const nextData = await progressGame(sessionId, choiceId, turnNumber)
      setGameData(nextData)
      setIsTypingComplete(false)
      window.scrollTo(0, 0)
    } catch (error) {
      if (!requireReauthentication(error)) {
        console.error(error)
        alert(getApiErrorMessage(error))
      }
    } finally {
      setIsLoading(false)
    }
  }

  if (authState !== 'authenticated') {
    return (
      <AccessScreen
        authState={authState}
        password={password}
        authMessage={authMessage}
        isLoading={isLoading}
        onPasswordChange={setPassword}
        onLogin={handleLogin}
      />
    )
  }

  if (gameData) {
    return (
      <GamePlayScreen
        gameData={gameData}
        mainImageUrl={mainImageUrl}
        isLoading={isLoading}
        isTypingComplete={isTypingComplete}
        onTypingComplete={() => setIsTypingComplete(true)}
        onChoice={handleChoice}
        onReturnToStart={() => setGameData(null)}
        onAuthError={requireReauthentication}
      />
    )
  }

  return (
    <GameSetupScreen
      world={world}
      character={character}
      isLoading={isLoading}
      onWorldChange={setWorld}
      onCharacterChange={setCharacter}
      onStart={handleStartGame}
    />
  )
}

export default App
