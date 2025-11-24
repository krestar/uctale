// src/App.jsx
import { useState, useEffect } from 'react'
import { initGame, progressGame } from './api/gameApi'
import GameImage from './components/GameImage'
import axios from 'axios'
import './App.css'

function App() {
    // [상태 추가] 인증 여부 (로컬 스토리지에 저장하여 새로고침 해도 유지)
    const [isAuthenticated, setIsAuthenticated] = useState(
        localStorage.getItem('uctale_auth') === 'true'
    );
    const [passwordInput, setPasswordInput] = useState('');

    const [world, setWorld] = useState('');
    const [character, setCharacter] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [gameData, setGameData] = useState(null);

    const sessionId = gameData?.characterImageUrl;

    // 비밀번호 확인 함수
    const handleLogin = async () => {
        try {
            await axios.post('http://localhost:8080/api/game/verify-password', {
                password: passwordInput
            });

            // 성공 시
            setIsAuthenticated(true);
            localStorage.setItem('uctale_auth', 'true'); // 로그인 상태 저장
        } catch (error) {
            alert("비밀번호가 틀렸습니다.");
            setPasswordInput('');
        }
    };

    const handleStartGame = async () => {
        if (!world || !character) {
            alert("세계관과 캐릭터 설정을 모두 입력해주세요!");
            return;
        }
        setIsLoading(true);
        try {
            const data = await initGame(world, character);
            setGameData(data);
        } catch (error) {
            alert("오류가 발생했습니다.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleChoice = async (choiceId) => {
        if (!sessionId) return;
        setIsLoading(true);
        try {
            const nextData = await progressGame(sessionId, choiceId);
            setGameData(nextData);
            window.scrollTo(0, 0);
        } catch (error) {
            alert("오류가 발생했습니다.");
        } finally {
            setIsLoading(false);
        }
    };

    // 1. 인증되지 않은 경우: 비밀번호 입력 화면 표시
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

    // 2. 게임 진행 중 화면
    if (gameData) {
        return (
            <div className="container">
                <h1>📖 {gameData.title}</h1>
                <div style={{ margin: '20px 0' }}>
                    <GameImage src={gameData.mainImageUrl} alt="Game Scene" />
                </div>
                <div style={{ textAlign: 'left', background: '#1e1e1e', padding: '20px', borderRadius: '8px', marginBottom: '20px', lineHeight: '1.6' }}>
                    <p>{gameData.storyText}</p>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {gameData.choices.map((choice) => (
                        <button
                            key={choice.id}
                            className="start-btn"
                            style={{ marginTop: 0, fontSize: '1rem' }}
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

    // 3. 기본 화면 (게임 설정 입력)
    return (
        <div className="container">
            <h1>UCTale(당신이 만들어가는 이야기)</h1>

            <div className="input-group">
                <label>🪐 어떤 세계관인가요?</label>
                <input
                    type="text"
                    placeholder="예: 현대 서울 좀비 아포칼립스"
                    value={world}
                    onChange={(e) => setWorld(e.target.value)}
                />
            </div>

            <div className="input-group">
                <label>👤 당신은 누구인가요?</label>
                <textarea
                    rows="3"
                    placeholder="예: 30대 평범한 직장인 김대리"
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