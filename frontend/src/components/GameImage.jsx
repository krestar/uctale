import { useState, useEffect } from 'react';

const GameImage = ({ src, alt }) => {
    const [isLoading, setIsLoading] = useState(true);

    // 이미지 주소(src)가 바뀔 때마다 로딩 상태를 true로 리셋
    useEffect(() => {
        setIsLoading(true);
    }, [src]);

    return (
        <div style={{
            position: 'relative',
            width: '100%',
            minHeight: '300px', // 로딩 중에도 공간 확보
            backgroundColor: '#000',
            borderRadius: '8px',
            overflow: 'hidden',
            border: '1px solid #333',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center'
        }}>
            {/* 1. 로딩 중에 보여줄 화면 (스피너 + 멘트) */}
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

            {/* 2. 실제 이미지 (로딩 전에는 숨김 처리, 로딩 완료 시 페이드인) */}
            <img
                src={src}
                alt={alt}
                onLoad={() => setIsLoading(false)} // 로딩 완료 시 호출됨
                style={{
                    width: '100%',
                    display: 'block',
                    opacity: isLoading ? 0 : 1, // 로딩 중엔 투명하게
                    transition: 'opacity 0.5s ease-in-out' // 부드럽게 나타나기
                }}
            />

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