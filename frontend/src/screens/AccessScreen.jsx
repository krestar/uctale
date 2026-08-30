import BrandHeader from '../components/BrandHeader'

function AccessScreen({
  authState,
  password,
  authMessage,
  isLoading,
  onPasswordChange,
  onLogin,
}) {
  const isChecking = authState === 'checking'

  return (
    <div className="app-shell">
      <BrandHeader />
      <main className="screen screen--narrow">
        <section className="folio-panel access-panel">
          <p className="eyebrow">Shared beta</p>
          <h1 className="screen-title">{isChecking ? '접근 세션 확인 중' : '공유 베타 접근'}</h1>

          {isChecking ? (
            <p className="supporting-copy">기존 접근 세션을 확인하고 있습니다.</p>
          ) : (
            <form
              className="access-form"
              onSubmit={(event) => {
                event.preventDefault()
                onLogin()
              }}
            >
              <div className="field-group">
                <label htmlFor="access-password">접근 비밀번호</label>
                <input
                  id="access-password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => onPasswordChange(event.target.value)}
                />
                <p className="field-help">공유받은 베타 접근 비밀번호를 입력하세요.</p>
              </div>

              {authMessage && <p className="message message--error" role="alert">{authMessage}</p>}

              <button className="button button--primary" type="submit" disabled={isLoading}>
                {isLoading ? '확인 중...' : '입장하기'}
              </button>
            </form>
          )}
        </section>
      </main>
    </div>
  )
}

export default AccessScreen
