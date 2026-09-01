function GameRulesPanel({ rules, isProgressing }) {
  const stats = rules?.stats
  const skillCheck = rules?.skillCheck

  return (
    <section className="rules-panel" aria-labelledby="rules-panel-title" aria-busy={isProgressing}>
      <div className="rules-panel__heading">
        <p className="eyebrow">Character rules</p>
        <h2 id="rules-panel-title">캐릭터 능력치와 최근 판정</h2>
      </div>

      <div className="rules-panel__content">
        <section aria-labelledby="stats-title">
          <h3 id="stats-title">능력치</h3>
          {stats?.state === 'ready' ? (
            <>
              <dl className="stats-grid">
                {stats.items.map((stat) => (
                  <div className="stat-card" key={stat.key}>
                    <dt>{stat.label}</dt>
                    <dd>
                      <strong>{stat.score}</strong>
                      <span>수정치 {stat.modifierText}</span>
                    </dd>
                  </div>
                ))}
              </dl>
              {stats.notice && <p className="rules-panel__notice" role="status">{stats.notice}</p>}
            </>
          ) : (
            <p className="rules-panel__notice" role="status">{stats?.message ?? '능력치 정보를 표시할 수 없습니다.'}</p>
          )}
        </section>

        <section className="skill-check-card" aria-labelledby="skill-check-title">
          <div className="skill-check-card__heading">
            <h3 id="skill-check-title">최근 Skill Check</h3>
            {skillCheck?.state === 'ready' && (
              <span className={`skill-check-outcome skill-check-outcome--${skillCheck.tone}`}>
                {skillCheck.outcomeLabel}
              </span>
            )}
          </div>

          {skillCheck?.state === 'ready' ? (
            <>
              <p className="skill-check-card__summary">
                {skillCheck.turnLabel} · {skillCheck.statLabel}
              </p>
              <dl className="skill-check-grid">
                <div><dt>주사위</dt><dd>{skillCheck.rawRoll}</dd></div>
                <div><dt>능력 수정치</dt><dd>{skillCheck.statModifierText}</dd></div>
                <div><dt>상황 수정치</dt><dd>{skillCheck.situationalModifierText}</dd></div>
                <div><dt>DC</dt><dd>{skillCheck.dc}</dd></div>
                <div><dt>합계</dt><dd>{skillCheck.total}</dd></div>
              </dl>
              {skillCheck.notice && <p className="rules-panel__notice" role="status">{skillCheck.notice}</p>}
            </>
          ) : (
            <p className="rules-panel__notice" role="status">
              {skillCheck?.message ?? '최근 Skill Check 결과를 표시할 수 없습니다.'}
            </p>
          )}
        </section>
      </div>
    </section>
  )
}

export default GameRulesPanel
