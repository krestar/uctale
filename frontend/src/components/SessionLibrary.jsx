import GameImage from './GameImage'
import { canOpenSession, SESSION_STATUS_HELP, SESSION_STATUS_LABELS } from '../session/sessionRecovery.js'
import '../session.css'

function formatUpdatedAt(value) {
  if (!value) return '저장 시각 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '저장 시각 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function SessionLibrary({ sessions, isLoading, error, hasMore, onLoadMore, resumingSessionId, onResume, onReload, onAuthError }) {
  return (
    <section className="session-library" aria-labelledby="session-library-title" aria-busy={isLoading}>
      <div className="session-library__heading">
        <div>
          <p className="eyebrow">저장된 이야기</p>
          <h2 id="session-library-title">이어갈 이야기</h2>
        </div>
        <button className="button button--secondary button--compact" type="button" onClick={onReload} disabled={isLoading}>
          새로고침
        </button>
      </div>

      <p className="session-library__help">
        UCTale은 장면이 끝날 때마다 자동 저장합니다. 처리 중이거나 실패한 요청이 있어도 마지막으로 저장된 장면은 그대로 보존됩니다.
      </p>

      {isLoading && <p className="status-message" role="status">저장된 이야기를 불러오고 있습니다.</p>}

      {error && (
        <div className="message message--error request-feedback" role="alert">
          <p>{error}</p>
          <button className="button button--secondary button--compact" type="button" onClick={onReload} disabled={isLoading}>
            다시 불러오기
          </button>
        </div>
      )}

      {!isLoading && !error && sessions.length === 0 && (
        <div className="session-library__empty">
          <p>아직 저장된 이야기가 없습니다.</p>
          <span>아래에서 새 모험을 시작하면 첫 완료 장면부터 자동 저장됩니다.</span>
        </div>
      )}

      {sessions.length > 0 && (
        <div className="session-list">
          {sessions.map((session) => {
            const isResuming = resumingSessionId === session.sessionId
            const canResume = canOpenSession(session)
            return (
              <article className="session-card" key={session.sessionId}>
                <div className="session-card__thumbnail">
                  {session.thumbnailUrl ? (
                    <GameImage
                      src={session.thumbnailUrl}
                      alt=""
                      onAuthError={onAuthError}
                      fallbackContent={<div className="session-card__thumbnail-empty" aria-hidden="true">UCTale</div>}
                    />
                  ) : (
                    <div className="session-card__thumbnail-empty" aria-hidden="true">UCTale</div>
                  )}
                </div>
                <div className="session-card__body">
                  <div className="session-card__meta">
                    <span className={`session-status session-status--${session.status?.toLowerCase() || 'ready'}`}>
                      {SESSION_STATUS_LABELS[session.status] || session.status}
                    </span>
                    <span>{session.currentTurn}번째 장면</span>
                    <span>{formatUpdatedAt(session.updatedAt)}</span>
                  </div>
                  <h3>{session.title}</h3>
                  <p>{SESSION_STATUS_HELP[session.status] ?? '저장된 이야기를 확인해 주세요.'}</p>
                  <button
                    className="button button--secondary button--compact"
                    type="button"
                    disabled={!canResume || isResuming}
                    onClick={() => onResume(session.sessionId)}
                  >
                    {isResuming ? '마지막 장면 불러오는 중...' : '이어서 하기'}
                  </button>
                </div>
              </article>
            )
          })}
        </div>
      )}
      {hasMore && <button className="button button--secondary button--compact" type="button" disabled={isLoading} onClick={onLoadMore}>
        {isLoading ? '더 불러오는 중...' : '저장된 이야기 더 보기'}
      </button>}
    </section>
  )
}

export default SessionLibrary
