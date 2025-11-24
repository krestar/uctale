import { useState, useEffect } from 'react'
import { initGame, progressGame, verifyPassword } from './api/gameApi'
import GameImage from './components/GameImage'
import TypewriterText from './components/TypewriterText'
import './App.css'

function App() {
    // 로컬 스토리지에서 인증 상태 확인
    const [isAuthenticated, setIsAuthenticated] = useState(
        localStorage.getItem('uctale_auth') === 'true'
    );
    const [passwordInput, setPasswordInput] = useState('');

    const [world, setWorld] = useState('');
    const [character, setCharacter] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [gameData, setGameData] = useState(null);
    const [isTypingComplete, setIsTypingComplete] = useState(false);

    const sessionId = gameData?.characterImageUrl;

    // 비밀번호 확인 함수
    const handleLogin = async () => {
        try {
            await verifyPassword(passwordInput);

            // 성공 시
            setIsAuthenticated(true);
            localStorage.setItem('uctale_auth', 'true');
        } catch (error) {
            alert("비밀번호가 틀렸습니다.");
            setPasswordInput('');
        }
    };

    // 게임 시작 (초기화)
    const handleStartGame = async () => {
        if (!world || !character) {
            alert("세계관과 캐릭터 설정을 모두 입력해주세요!");
            return;
        }
        setIsLoading(true);
        try {
            const data = await initGame(world, character);
            setGameData(data);
            setIsTypingComplete(false);
        } catch (error) {
            console.error(error);
            alert("오류가 발생했습니다.");
        } finally {
            setIsLoading(false);
        }
    };

    // 선택지 클릭 (게임 진행)
    const handleChoice = async (choiceId) => {
        if (!sessionId) return;
        setIsLoading(true);
        try {
            const nextData = await progressGame(sessionId, choiceId);
            setGameData(nextData);
            setIsTypingComplete(false);
            window.scrollTo(0, 0);
        } catch (error) {
            console.error(error);
            alert("오류가 발생했습니다.");
        } finally {
            setIsLoading(false);
        }
    };

    // 1. 인증 화면
    if (!isAuthenticated) {
        return (
            <div className="container">
                <h1>UCTale 접근 제한</h1>
                <p style={{ marginBottom: '20px', color: '#aaa' }}>
                    이 프로젝트는 AI API 자원을 사용하므로<br/>접근 권한이 필요합니다.
                </p>
                <div className="input-group">
                    <label>🔒 접근 비밀번호</label>
                    <input
                        type="password"
                        placeholder="비밀번호를 입력하세요"
                        value={passwordInput}
                        onChange={(e) => setPasswordInput(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
                    />
                </div>
                <button className="start-btn" onClick={handleLogin}>
                    확인
                </button>
            </div>
        );
    }

    // 2. 게임 플레이 화면
    if (gameData) {
        return (
            <div className="container">
                <h1>📖 {gameData.title}</h1>

                {/* 이미지 영역 */}
                <div style={{ margin: '20px 0', position: 'relative' }}>
                    <GameImage src={gameData.mainImageUrl} alt="Game Scene" />
                </div>

                {/* 스토리 텍스트 (타이핑 효과) */}
                <div style={{ textAlign: 'left', background: '#1e1e1e', padding: '20px', borderRadius: '8px', marginBottom: '20px' }}>
                    <TypewriterText
                        text={gameData.storyText}
                        onComplete={() => setIsTypingComplete(true)}
                    />
                </div>

                {/* 선택지 버튼 */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {gameData.choices.map((choice) => (
                        <button
                            key={choice.id}
                            className="start-btn"
                            style={{ marginTop: 0, fontSize: '1rem', opacity: isTypingComplete ? 1 : 0.5 }}
                            onClick={() => handleChoice(choice.id)}
                            disabled={isLoading}
                        >
                            {choice.text}
                        </button>
                    ))}
                </div>

                <button
                    className="start-btn"
                    style={{ backgroundColor: '#555', marginTop: '30px' }}
                    onClick={() => setGameData(null)}
                    disabled={isLoading}
                >
                    처음으로
                </button>
            </div>
        );
    }

    // 3. 기본 입력 화면
    return (
        <div className="container">
            <h1>
                UCTale
                <span className="subtitle">당신이 만들어가는 이야기</span>
            </h1>

            <div className="input-group">
                <label>🪐 어떤 세계관인가요?</label>
                <textarea
                    rows="3"
                    placeholder="예: 현대 서울 좀비 아포칼립스, 서울에 핵미사일이 발사된 상황, 눈을 떴더니 고양이"
                    value={world}
                    onChange={(e) => setWorld(e.target.value)}
                />
            </div>

            <div className="input-group">
                <label>👤 당신은 누구인가요?</label>
                <textarea
                    rows="3"
                    placeholder="예: 지하철로 출근하는 30대 회사원 김대리, 눈을 떴더니 이세계로 전이된 대학생, 사람 말을 할 수 있게 된 고양이"
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