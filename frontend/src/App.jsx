import { useState } from 'react'
import { initGame, progressGame, resolveGameAssetUrl } from './api/gameApi'
import GameImage from './components/GameImage'
import TypewriterText from './components/TypewriterText'
import './App.css'

function App() {
    const [world, setWorld] = useState('');
    const [character, setCharacter] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [gameData, setGameData] = useState(null);
    const [isTypingComplete, setIsTypingComplete] = useState(false);

    const sessionId = gameData?.sessionId;
    const mainImageUrl = resolveGameAssetUrl(gameData?.mainImageUrl);

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

    if (gameData) {
        return (
            <div className="container">
                <h1>📖 {gameData.title}</h1>

                <div className="image-container">
                    <GameImage key={mainImageUrl} src={mainImageUrl} alt="Game Scene" />
                </div>

                <div className="story-box">
                    <TypewriterText
                        key={gameData.storyText}
                        text={gameData.storyText}
                        onComplete={() => setIsTypingComplete(true)}
                    />
                </div>

                <div className="choice-wrapper">
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
                    style={{ backgroundColor: '#555', marginTop: '30px', width: 'auto', minWidth: '200px' }}
                    onClick={() => setGameData(null)}
                    disabled={isLoading}
                >
                    처음으로
                </button>
            </div>
        );
    }

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
