import BrandHeader from '../components/BrandHeader'

function AccessScreen({
  authState,
  password,
  authMessage,
  authMessageKind,
  isLoading,
  onPasswordChange,
  onLogin,
}) {
  const isChecking = authState === 'checking'
  const passwordDescribedBy = authMessageKind === 'validation' && authMessage
    ? 'access-password-help access-password-error'
    : 'access-password-help'

  return (
    <div className="app-shell">
      <BrandHeader />
      <main className="screen screen--narrow">
        <section className="folio-panel access-panel" aria-busy={isChecking || isLoading}>
          <p className="eyebrow">Shared beta</p>
          <h1 className="screen-title">{isChecking ? '접근 세션 확인 중' : '공유 베타 접근'}</h1>

          {isChecking ? (
            <p className="supporting-copy" role="status">기존 접근 세션을 확인하고 있습니다.</p>
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
                  aria-invalid={authMessageKind === 'validation' && Boolean(authMessage)}
                  aria-describedby={passwordDescribedBy}
                  value={password}
                  onChange={(event) => onPasswordChange(event.target.value)}
                />
                <p id="access-password-help" className="field-help">공유받은 베타 접근 비밀번호를 입력하세요.</p>
                {authMessageKind === 'validation' && authMessage && (
                  <p id="access-password-error" className="field-error">{authMessage}</p>
                )}
              </div>

              {authMessageKind !== 'validation' && authMessage && (
                <p className="message message--error" role="alert">{authMessage}</p>
              )}

              {isLoading && <p className="status-message" role="status">접근 권한을 확인하고 있습니다.</p>}

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
