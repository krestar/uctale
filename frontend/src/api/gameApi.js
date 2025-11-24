import axios from 'axios';

// 백엔드 서버 주소
const BASE_URL = 'http://localhost:8080/api/game';

/**
 * 게임 초기화 요청 (POST /api/game/init)
 * @param {string} worldSetting - 사용자가 입력한 세계관
 * @param {string} characterSetting - 사용자가 입력한 캐릭터 정보
 * @returns {Promise<Object>} - { storyText, choices, mainImageUrl, sessionId }
 */
export const initGame = async (worldSetting, characterSetting) => {
    try {
        const response = await axios.post(`${BASE_URL}/init`, {
            worldSetting,
            characterSetting
        });
        return response.data;
    } catch (error) {
        console.error("게임 초기화 API 호출 실패:", error);
        throw error;
    }
};

/**
 * [추가] 게임 진행 요청 (POST /api/game/progress)
 * @param {string} sessionId - 현재 게임 세션 ID
 * @param {number} choiceId - 사용자가 선택한 선택지 번호
 * @returns {Promise<Object>} - { storyText, choices, mainImageUrl, sessionId }
 */
export const progressGame = async (sessionId, choiceId) => {
    try {
        const response = await axios.post(`${BASE_URL}/progress`, {
            sessionId,
            choiceId
        });
        return response.data;
    } catch (error) {
        console.error("게임 진행 API 호출 실패:", error);
        throw error;
    }
};