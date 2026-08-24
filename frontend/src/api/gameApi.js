import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/game';

export const initGame = async (worldSetting, characterSetting) => {
    const response = await axios.post(`${BASE_URL}/init`, {
        worldSetting,
        characterSetting
    });
    return response.data;
};

export const progressGame = async (sessionId, choiceId) => {
    const response = await axios.post(`${BASE_URL}/progress`, {
        sessionId,
        choiceId
    });
    return response.data;
};

export const verifyPassword = async (password) => {
    await axios.post(`${BASE_URL}/verify-password`, {
        password
    });
    return true;
};
