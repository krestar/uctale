import { useState, useEffect, useRef } from 'react';

const TypewriterText = ({ text, speed = 30, onComplete }) => {
    const [displayedText, setDisplayedText] = useState('');
    const indexRef = useRef(0);

    useEffect(() => {
        setDisplayedText('');
        indexRef.current = 0;

        const intervalId = setInterval(() => {
            if (indexRef.current < text.length) {
                setDisplayedText((prev) => prev + text.charAt(indexRef.current));
                indexRef.current++;
            } else {
                clearInterval(intervalId);
                if (onComplete) onComplete();
            }
        }, speed);

        return () => clearInterval(intervalId);
    }, [text, speed, onComplete]);

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