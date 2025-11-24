import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/game';

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

export const verifyPassword = async (password) => {
    try {
        await axios.post(`${BASE_URL}/verify-password`, {
            password
        });
        return true; // 성공
    } catch (error) {
        throw error; // 실패
    }
};