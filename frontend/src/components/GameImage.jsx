import { useEffect, useRef, useState } from 'react';
import { fetchGameImage } from '../api/gameApi';
import { isAccessAuthError } from '../api/apiError';

const GameImage = ({ src, alt, onAuthError }) => {
    const [imageSrc, setImageSrc] = useState(null);
    const [isLoading, setIsLoading] = useState(Boolean(src));
    const [hasError, setHasError] = useState(false);
    const onAuthErrorRef = useRef(onAuthError);

    useEffect(() => {
        onAuthErrorRef.current = onAuthError;
    }, [onAuthError]);

    useEffect(() => {
        let cancelled = false;
        let objectUrl = null;

        if (!src) {
            setImageSrc(null);
            setIsLoading(false);
            setHasError(false);
            return undefined;
        }

        setIsLoading(true);
        setHasError(false);
        setImageSrc(null);

        fetchGameImage(src)
            .then((blob) => {
                if (cancelled) return;
                objectUrl = URL.createObjectURL(blob);
                setImageSrc(objectUrl);
            })
            .catch((error) => {
                if (cancelled) return;
                if (isAccessAuthError(error) && onAuthErrorRef.current) {
                    onAuthErrorRef.current(error);
                    return;
                }
                setHasError(true);
            })
            .finally(() => {
                if (!cancelled) setIsLoading(false);
            });

        return () => {
            cancelled = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [src]);

    return (
        <div style={{
            position: 'relative',
            width: '100%',
            minHeight: '300px',
            backgroundColor: '#000',
            borderRadius: '8px',
            overflow: 'hidden',
            border: '1px solid #333',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center'
        }}>
            {isLoading && (
                <div style={{
                    position: 'absolute',
                    textAlign: 'center',
                    color: '#888',
                    zIndex: 1
                }}>
                    <div className="spinner" style={{
                        margin: '0 auto 10px',
                        width: '40px',
                        height: '40px',
                        border: '4px solid #333',
                        borderTop: '4px solid #bb86fc',
                        borderRadius: '50%',
                        animation: 'spin 1s linear infinite'
                    }}></div>
                    <p style={{ fontSize: '0.9rem' }}>AI 화가가 스케치 중입니다...</p>
                </div>
            )}

            {hasError && !isLoading && (
                <p style={{ color: '#aaa', textAlign: 'center' }}>이미지를 불러오지 못했습니다.</p>
            )}

            {imageSrc && (
                <img
                    src={imageSrc}
                    alt={alt}
                    style={{ width: '100%', display: 'block' }}
                />
            )}

            <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
        </div>
    );
};

export default GameImage;
