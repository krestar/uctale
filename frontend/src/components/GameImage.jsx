import { useState } from 'react';

const GameImage = ({ src, alt }) => {
    const [isLoading, setIsLoading] = useState(true);

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

            <img
                src={src}
                alt={alt}
                onLoad={() => setIsLoading(false)}
                style={{
                    width: '100%',
                    display: 'block',
                    opacity: isLoading ? 0 : 1,
                    transition: 'opacity 0.5s ease-in-out'
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
