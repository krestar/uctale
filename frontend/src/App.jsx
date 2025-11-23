// src/App.jsx
import { useState } from 'react'
import { initGame } from './api/gameApi'
import GameImage from './components/GameImage'
import './App.css'

function App() {
    const [world, setWorld] = useState('');
    const [character, setCharacter] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [gameData, setGameData] = useState(null);

    const handleStartGame = async () => {
        if (!world || !character) {
            alert("세계관과 캐릭터 설정을 모두 입력해주세요!");
            return;
        }

        setIsLoading(true);
        console.log("게임 시작 요청:", { world, character });

        try {
            // 실제 백엔드 API 호출
            const data = await initGame(world, character);
            console.log("응답 데이터:", data);

            setGameData(data);

        } catch (error) {
            console.error("에러 발생:", error);
            alert("서버와 연결할 수 없거나 오류가 발생했습니다.\n(백엔드 서버가 켜져 있는지 확인해주세요!)");
        } finally {
            setIsLoading(false);
        }
    };

    // 게임 데이터가 있으면 '게임 플레이 화면' 렌더링
    if (gameData) {
        return (
            <div className="container">
                <h1>📖 {world}의 이야기</h1>

                {/* GameImage 컴포넌트 사용 (로딩 처리 포함) */}
                <div style={{ margin: '20px 0' }}>
                    <GameImage src={gameData.mainImageUrl} alt="Game Scene" />
                </div>

                {/* 스토리 텍스트 */}
                <div style={{ textAlign: 'left', background: '#1e1e1e', padding: '20px', borderRadius: '8px', marginBottom: '20px', lineHeight: '1.6' }}>
                    <p>{gameData.storyText}</p>
                </div>

                {/* 선택지 버튼들 */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {gameData.choices.map((choice) => (
                        <button key={choice.id} className="start-btn" style={{ marginTop: 0, fontSize: '1rem' }}>
                            {choice.text}
                        </button>
                    ))}
                </div>

                {/* 처음으로 돌아가기 */}
                <button
                    className="start-btn"
                    style={{ backgroundColor: '#555', marginTop: '30px' }}
                    onClick={() => setGameData(null)}
                >
                    처음으로
                </button>
            </div>
        );
    }

    // 기본 화면 (입력 폼)
    return (
        <div className="container">
            <h1>UCTale(당신이 만들어가는 이야기)</h1>

            <div className="input-group">
                <label>🪐 어떤 세계관인가요?</label>
                <input
                    type="text"
                    placeholder="예: 현대 서울 좀비 아포칼립스, 마법이 없는 중세 판타지..."
                    value={world}
                    onChange={(e) => setWorld(e.target.value)}
                />
            </div>

            <div className="input-group">
                <label>👤 당신은 누구인가요?</label>
                <textarea
                    rows="3"
                    placeholder="예: 30대 평범한 직장인 김대리, 퇴근길에 지하철에 갇혔다."
                    value={character}
                    onChange={(e) => setCharacter(e.target.value)}
                />
            </div>

            <button
                className="start-btn"
                onClick={handleStartGame}
                disabled={isLoading}
            >
                {isLoading ? "운명을 생성하는 중..." : "모험 시작하기"}
            </button>
        </div>
    )
}

export default App