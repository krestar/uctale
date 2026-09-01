import axios from 'axios';
import { mapGameResponse } from './gameResponseMapper.js';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/game';
const API_ORIGIN = new URL(BASE_URL).origin;
const apiClient = axios.create({
    withCredentials: true,
    headers: { 'X-UCTale-Client': 'web' }
});

export const createIdempotencyKey = () => crypto.randomUUID();

export const initGame = async (worldSetting, characterSetting, idempotencyKey) => {
    const response = await apiClient.post(`${BASE_URL}/init`, {
        worldSetting,
        characterSetting
    }, {
        headers: { 'Idempotency-Key': idempotencyKey }
    });
    return mapGameResponse(response.data);
};

export const progressGame = async (sessionId, choice, expectedTurn, idempotencyKey) => {
    const response = await apiClient.post(`${BASE_URL}/progress`, {
        sessionId,
        choiceId: choice.id,
        expectedTurn,
        actionToken: choice.actionToken ?? null,
        actionType: choice.actionType ?? null,
        sourceTurn: choice.sourceTurn ?? null,
        arguments: choice.arguments ?? null
    }, {
        headers: { 'Idempotency-Key': idempotencyKey }
    });
    return mapGameResponse(response.data);
};

export const resolveGameAssetUrl = (assetUrl) => {
    if (!assetUrl || !assetUrl.startsWith('/')) {
        return assetUrl;
    }
    return new URL(assetUrl, API_ORIGIN).toString();
};

export const fetchGameImage = async (assetUrl) => {
    const response = await apiClient.get(resolveGameAssetUrl(assetUrl), {
        responseType: 'blob'
    });
    return response.data;
};

export const verifyPassword = async (password) => {
    await apiClient.post(`${BASE_URL}/verify-password`, { password });
};

export const checkAccessSession = async () => {
    await apiClient.get(`${BASE_URL}/access-session`);
};
