import { useEffect, useRef } from 'react'
import BrandHeader from '../components/BrandHeader'
import GameImage from '../components/GameImage'
import TypewriterText from '../components/TypewriterText'

function GamePlayScreen({
  gameData,
  mainImageUrl,
  isProgressing,
  isTypingComplete,
  pendingChoiceId,
  progressError,
  onTypingComplete,
  onChoice,
  onRetryChoice,
  onReturnToStart,
  onAuthError,
}) {
  const sceneHeadingRef = useRef(null)

  useEffect(() => {
    sceneHeadingRef.current?.focus()
  }, [gameData.turnNumber])

  const choicesDisabled = isProgressing || !isTypingComplete

  return (
    <div className="app-shell">
      <BrandHeader compact />
      <main className="screen gameplay-screen">
        <header className="scene-heading" tabIndex="-1" ref={sceneHeadingRef}>
          <p className="eyebrow">Current scene</p>
          <h1 className="scene-title">{gameData.title}</h1>
        </header>

        <div className="scene-frame">
          <GameImage
            src={mainImageUrl}
            alt={`${gameData.title} 장면 이미지`}
            onAuthError={onAuthError}
          />
        </div>

        <article className="story-section" aria-labelledby="story-heading">
          <h2 id="story-heading" className="sr-only">현재 이야기</h2>
          <TypewriterText
            text={gameData.storyText}
            onComplete={onTypingComplete}
          />
        </article>

        <section className="choices-section" aria-labelledby="choices-title" aria-busy={isProgressing}>
          <div className="choices-heading">
            <p className="eyebrow">Your choice</p>
            <h2 id="choices-title">다음 행동을 선택하세요.</h2>
          </div>

          {!isTypingComplete && !isProgressing && (
            <p className="status-message" role="status">본문이 모두 표시되면 선택할 수 있습니다.</p>
          )}

          {isProgressing && (
            <p className="status-message" role="status">선택의 결과와 다음 장면을 생성하고 있습니다.</p>
          )}

          <div className="choice-list">
            {gameData.choices.map((choice) => {
              const isPending = pendingChoiceId === choice.id
              return (
                <button
                  key={choice.id}
                  className={`choice-button${isPending ? ' choice-button--pending' : ''}`}
                  type="button"
                  onClick={() => onChoice(choice.id)}
                  disabled={choicesDisabled}
                  aria-describedby={isPending ? 'progress-status' : undefined}
                >
                  <span className="choice-button__marker" aria-hidden="true">→</span>
                  <span>{choice.text}</span>
                  {isPending && <span className="choice-button__state">진행 중</span>}
                </button>
              )
            })}
          </div>

          {isProgressing && <span id="progress-status" className="sr-only">선택을 처리하고 있습니다.</span>}

          {progressError && (
            <div className="message message--error progress-feedback" role="alert">
              <p className="progress-feedback__choice">
                실패한 선택: <strong>{progressError.choiceText}</strong>
              </p>
              <p>{progressError.message}</p>
              {progressError.canRetry && (
                <button
                  className="button button--secondary button--compact"
                  type="button"
                  onClick={onRetryChoice}
                  disabled={isProgressing}
                >
                  이 선택 다시 시도
                </button>
              )}
            </div>
          )}
        </section>

        <div className="secondary-actions">
          <button
            className="button button--secondary"
            type="button"
            onClick={onReturnToStart}
            disabled={isProgressing}
          >
            처음으로
          </button>
        </div>
      </main>
    </div>
  )
}

export default GamePlayScreen
