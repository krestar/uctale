import { useState, useEffect, useRef } from 'react';

const TypewriterText = ({ text, speed = 30, onComplete }) => {
    const [displayedText, setDisplayedText] = useState('');
    const indexRef = useRef(0);
    const onCompleteRef = useRef(onComplete);

    useEffect(() => {
        onCompleteRef.current = onComplete;
    }, [onComplete]);

    useEffect(() => {
        const intervalId = setInterval(() => {
            const currentIndex = indexRef.current;

            if (currentIndex < text.length) {
                setDisplayedText((prev) => prev + text.charAt(currentIndex));
                indexRef.current++;
            } else {
                clearInterval(intervalId);
                if (onCompleteRef.current) onCompleteRef.current();
            }
        }, speed);

        return () => clearInterval(intervalId);
    }, [text, speed]);

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
