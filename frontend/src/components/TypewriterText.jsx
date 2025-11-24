import { useState, useEffect, useRef } from 'react';

const TypewriterText = ({ text, speed = 30, onComplete }) => {
    const [displayedText, setDisplayedText] = useState('');
    const indexRef = useRef(0);

    // onComplete 함수가 바뀔 때마다 useEffect가 재실행되는 것을 방지하기 위해 Ref에 저장
    const onCompleteRef = useRef(onComplete);

    useEffect(() => {
        onCompleteRef.current = onComplete;
    }, [onComplete]);

    useEffect(() => {
        // 텍스트가 변경되었을 때만 초기화
        setDisplayedText('');
        indexRef.current = 0;

        const intervalId = setInterval(() => {
            // 중요: setState 내부가 아닌 외부에서 현재 인덱스를 캡처해야 함 (첫 글자 잘림 방지)
            const currentIndex = indexRef.current;

            if (currentIndex < text.length) {
                setDisplayedText((prev) => prev + text.charAt(currentIndex));
                indexRef.current++;
            } else {
                clearInterval(intervalId);
                // Ref에 저장된 최신 콜백 실행
                if (onCompleteRef.current) onCompleteRef.current();
            }
        }, speed);

        return () => clearInterval(intervalId);
    }, [text, speed]); // onComplete를 의존성에서 제거

    return (
        <p style={{
            whiteSpace: 'pre-wrap',
            lineHeight: '1.8',
            margin: 0
        }}>
            {displayedText}
        </p>
    );
};

export default TypewriterText;