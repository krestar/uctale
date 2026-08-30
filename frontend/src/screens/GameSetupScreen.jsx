import BrandHeader from '../components/BrandHeader'

function GameSetupScreen({
  world,
  character,
  fieldErrors,
  requestError,
  isLoading,
  onWorldChange,
  onCharacterChange,
  onStart,
  onRetry,
}) {
  const worldDescribedBy = fieldErrors.world
    ? 'world-setting-help world-setting-error'
    : 'world-setting-help'
  const characterDescribedBy = fieldErrors.character
    ? 'player-character-help player-character-error'
    : 'player-character-help'

  return (
    <div className="app-shell">
      <BrandHeader />
      <main className="screen screen--reading">
        <section className="setup-intro">
          <p className="eyebrow">Create a story</p>
          <h1 className="screen-title">당신이 살아갈 장면을 정해주세요.</h1>
          <p className="supporting-copy">
            세계와 주인공의 출발점만 적어주세요. 이후 이야기는 선택에 따라 이어집니다.
          </p>
        </section>

        <form
          className="setup-form"
          aria-busy={isLoading}
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            onStart()
          }}
        >
          <div className="field-group">
            <label htmlFor="world-setting">어떤 세계관인가요?</label>
            <p id="world-setting-help" className="field-help">
              장소, 시대, 장르, 현재 벌어진 사건처럼 이야기의 무대를 설명하세요.
            </p>
            <textarea
              id="world-setting"
              rows="4"
              required
              aria-invalid={Boolean(fieldErrors.world)}
              aria-describedby={worldDescribedBy}
              placeholder="예: 현대 서울 좀비 아포칼립스, 서울에 핵미사일이 발사된 상황, 눈을 떴더니 고양이"
              value={world}
              onChange={(event) => onWorldChange(event.target.value)}
            />
            {fieldErrors.world && (
              <p id="world-setting-error" className="field-error">{fieldErrors.world}</p>
            )}
          </div>

          <div className="field-group">
            <label htmlFor="player-character">당신은 누구인가요?</label>
            <p id="player-character-help" className="field-help">
              이름, 배경, 성격이나 지금 처한 상황을 간단히 적어주세요.
            </p>
            <textarea
              id="player-character"
              rows="4"
              required
              aria-invalid={Boolean(fieldErrors.character)}
              aria-describedby={characterDescribedBy}
              placeholder="예: 지하철로 출근하는 30대 회사원 김대리, 눈을 떴더니 이세계로 전이된 대학생, 사람 말을 할 수 있게 된 고양이"
              value={character}
              onChange={(event) => onCharacterChange(event.target.value)}
            />
            {fieldErrors.character && (
              <p id="player-character-error" className="field-error">{fieldErrors.character}</p>
            )}
          </div>

          {requestError && (
            <div className="message message--error request-feedback" role="alert">
              <p>{requestError}</p>
              <button className="button button--secondary button--compact" type="button" onClick={onRetry} disabled={isLoading}>
                다시 시도
              </button>
            </div>
          )}

          {isLoading && (
            <p className="status-message" role="status">
              스토리와 첫 장면을 생성하고 있습니다. 입력한 설정은 그대로 유지됩니다.
            </p>
          )}

          <button className="button button--primary button--wide" type="submit" disabled={isLoading}>
            {isLoading ? '스토리와 장면 생성 중...' : '모험 시작하기'}
          </button>
        </form>
      </main>
    </div>
  )
}

export default GameSetupScreen
