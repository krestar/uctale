import { useState } from 'react'
import './App.css'

function App() {
    // 사용자 입력을 저장할 State
    const [world, setWorld] = useState('');
    const [character, setCharacter] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    // 게임 시작 버튼 클릭 시 실행될 함수
    const handleStartGame = async () => {
        if (!world || !character) {
            alert("세계관과 캐릭터 설정을 모두 입력해주세요!");
            return;
        }

        setIsLoading(true); // 로딩 시작
        console.log("게임 시작 요청:", { world, character });

        try {
            // TODO: 백엔드 API (/api/game/init) 호출 로직이 들어갈 자리
            // 지금은 2초 뒤에 알림창만 띄웁니다.
            await new Promise(resolve => setTimeout(resolve, 2000));
            alert("API 연동 준비 완료! 백엔드로 데이터를 보낼 준비가 되었습니다.");

        } catch (error) {
            console.error("에러 발생:", error);
            alert("게임 시작 중 오류가 발생했습니다.");
        } finally {
            setIsLoading(false); // 로딩 끝
        }
    };

    return (
        <div className="container">
            <h1>UCTale (당신이 만들어가는 이야기)</h1>

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