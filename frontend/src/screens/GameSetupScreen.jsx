import BrandHeader from '../components/BrandHeader'

function GameSetupScreen({
  world,
  character,
  isLoading,
  onWorldChange,
  onCharacterChange,
  onStart,
}) {
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
              aria-describedby="world-setting-help"
              placeholder="예: 현대 서울의 좀비 아포칼립스. 한강 이남의 통신이 끊기기 시작했다."
              value={world}
              onChange={(event) => onWorldChange(event.target.value)}
            />
          </div>

          <div className="field-group">
            <label htmlFor="player-character">당신은 누구인가요?</label>
            <p id="player-character-help" className="field-help">
              이름, 배경, 성격이나 지금 처한 상황을 간단히 적어주세요.
            </p>
            <textarea
              id="player-character"
              rows="4"
              aria-describedby="player-character-help"
              placeholder="예: 지하철로 출근하던 30대 회사원 김대리. 평범하지만 위기에는 침착하다."
              value={character}
              onChange={(event) => onCharacterChange(event.target.value)}
            />
          </div>

          <button className="button button--primary button--wide" type="submit" disabled={isLoading}>
            {isLoading ? '운명을 생성하는 중...' : '모험 시작하기'}
          </button>
        </form>
      </main>
    </div>
  )
}

export default GameSetupScreen
