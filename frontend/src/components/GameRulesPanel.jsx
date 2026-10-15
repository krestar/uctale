import './GameRulesPanel.css'

function GameRulesPanel({ rules, isProgressing }) {
  const stats = rules?.stats
  const skillCheck = rules?.skillCheck
  const gameplay = rules?.gameplay

  return (
    <section className="rules-panel" aria-labelledby="rules-panel-title" aria-busy={isProgressing}>
      <div className="rules-panel__heading">
        <p className="eyebrow">캐릭터 정보</p>
        <h2 id="rules-panel-title">내 캐릭터와 행동 결과</h2>
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
                      <span>판정 보정 {stat.modifierText}</span>
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

        {gameplay && (
          <section className="skill-check-card" aria-labelledby="gameplay-state-title">
            <h3 id="gameplay-state-title">현재 상태</h3>
            <p>체력 {gameplay.hp} / {gameplay.maxHp} · 기력 {gameplay.mp} / {gameplay.maxMp}</p>
            {gameplay.items.length > 0 && (
              <p>가지고 있는 물품: {gameplay.items.map((item) => item.id.replace(/^item:/, '').replaceAll('-', ' ') + ' ' + item.quantity + '개' + (item.equipped ? ' (착용 중)' : '')).join(', ')}</p>
            )}
            {gameplay.relationships.length > 0 && (
              <p>인물과의 친밀도: {gameplay.relationships.map((npc) => npc.npcId.replace(/^npc:/, '').replaceAll('-', ' ') + ' ' + npc.affinity).join(', ')}</p>
            )}
            {gameplay.quests?.length > 0 && (
              <p>목표 진행: {gameplay.quests.map((quest) => quest.questId.replace('quest.v2.', '').replaceAll('-', ' ') + ' (' + ({ AVAILABLE: '시작 전', ACTIVE: '진행 중', COMPLETED: '완료', FAILED: '실패' }[quest.status] || '확인 필요') + ')').join(', ')}</p>
            )}
            {gameplay.statuses?.length > 0 && (
              <p>현재 효과: {gameplay.statuses.map((effect) => effect.effectId.replace(/^status:/, '').replaceAll('-', ' ') + ' (' + effect.remainingTurns + '장면 남음)').join(', ')}</p>
            )}
            {gameplay.combatStatus && <p>전투 상태: {{ PENDING: '시작 전', ACTIVE: '전투 중', RESOLVED: '종료', ESCAPED: '이탈' }[gameplay.combatStatus] || '상태 확인 필요'}</p>}
          </section>
        )}
        <section className="skill-check-card" aria-labelledby="skill-check-title">
          <div className="skill-check-card__heading">
            <h3 id="skill-check-title">최근 행동 판정</h3>
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
                <div><dt>능력치 보정</dt><dd>{skillCheck.statModifierText}</dd></div>
                <div><dt>상황 보정</dt><dd>{skillCheck.situationalModifierText}</dd></div>
                <div><dt>성공 기준</dt><dd>{skillCheck.dc}</dd></div>
                <div><dt>판정 합계</dt><dd>{skillCheck.total}</dd></div>
              </dl>
              {skillCheck.notice && <p className="rules-panel__notice" role="status">{skillCheck.notice}</p>}
            </>
          ) : (
            <p className="rules-panel__notice" role="status">
              {skillCheck?.message ?? '최근 행동 판정 결과를 표시할 수 없습니다.'}
            </p>
          )}
        </section>
      </div>
    </section>
  )
}

export default GameRulesPanel
