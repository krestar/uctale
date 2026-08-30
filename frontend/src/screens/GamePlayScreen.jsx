import BrandHeader from '../components/BrandHeader'
import GameImage from '../components/GameImage'
import TypewriterText from '../components/TypewriterText'

function GamePlayScreen({
  gameData,
  mainImageUrl,
  isLoading,
  isTypingComplete,
  onTypingComplete,
  onChoice,
  onReturnToStart,
  onAuthError,
}) {
  return (
    <div className="app-shell">
      <BrandHeader compact />
      <main className="screen gameplay-screen">
        <header className="scene-heading">
          <p className="eyebrow">Current scene</p>
          <h1 className="scene-title">{gameData.title}</h1>
        </header>

        <div className="scene-frame">
          <GameImage
            key={mainImageUrl}
            src={mainImageUrl}
            alt={`${gameData.title} 장면`}
            onAuthError={onAuthError}
          />
        </div>

        <article className="story-section">
          <TypewriterText
            key={gameData.storyText}
            text={gameData.storyText}
            onComplete={onTypingComplete}
          />
        </article>

        <section className="choices-section" aria-labelledby="choices-title">
          <div className="choices-heading">
            <p className="eyebrow">Your choice</p>
            <h2 id="choices-title">다음 행동을 선택하세요.</h2>
          </div>

          <div className="choice-list">
            {gameData.choices.map((choice) => (
              <button
                key={choice.id}
                className={`choice-button${isTypingComplete ? '' : ' choice-button--pending'}`}
                type="button"
                onClick={() => onChoice(choice.id)}
                disabled={isLoading}
              >
                <span className="choice-button__marker" aria-hidden="true">→</span>
                <span>{choice.text}</span>
              </button>
            ))}
          </div>
        </section>

        <div className="secondary-actions">
          <button
            className="button button--secondary"
            type="button"
            onClick={onReturnToStart}
            disabled={isLoading}
          >
            처음으로
          </button>
        </div>
      </main>
    </div>
  )
}

export default GamePlayScreen
