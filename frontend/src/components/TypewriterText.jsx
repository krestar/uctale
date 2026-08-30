import { useEffect, useRef, useState } from 'react'

const TypewriterText = ({ text, speed = 30, onComplete }) => {
  const [displayedText, setDisplayedText] = useState('')
  const indexRef = useRef(0)
  const onCompleteRef = useRef(onComplete)

  useEffect(() => {
    onCompleteRef.current = onComplete
  }, [onComplete])

  useEffect(() => {
    const intervalId = setInterval(() => {
      const currentIndex = indexRef.current

      if (currentIndex < text.length) {
        setDisplayedText((previousText) => previousText + text.charAt(currentIndex))
        indexRef.current += 1
      } else {
        clearInterval(intervalId)
        if (onCompleteRef.current) onCompleteRef.current()
      }
    }, speed)

    return () => clearInterval(intervalId)
  }, [text, speed])

  return <p className="story-copy">{displayedText}</p>
}

export default TypewriterText
