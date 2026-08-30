import { useEffect, useRef, useState } from 'react'
import {
  advanceTypewriter,
  createTypewriterState,
  getDisplayedText,
  prefersReducedMotion,
  skipTypewriter,
} from './typewriterBehavior'

const TypewriterText = ({ text, speed = 30, onComplete }) => {
  const [state, setState] = useState(() => createTypewriterState(text, prefersReducedMotion()))
  const onCompleteRef = useRef(onComplete)
  const completionNotifiedRef = useRef(false)

  useEffect(() => {
    onCompleteRef.current = onComplete
  }, [onComplete])

  useEffect(() => {
    if (state.isComplete) {
      if (!completionNotifiedRef.current) {
        completionNotifiedRef.current = true
        onCompleteRef.current?.()
      }
      return undefined
    }

    const timeoutId = window.setTimeout(() => {
      setState((current) => advanceTypewriter(current))
    }, speed)

    return () => window.clearTimeout(timeoutId)
  }, [state, speed])

  const handleSkip = () => {
    setState((current) => skipTypewriter(current))
  }

  return (
    <div className="typewriter">
      <p className="sr-only">{text}</p>
      <p className="story-copy" aria-hidden="true">{getDisplayedText(state)}</p>
      {!state.isComplete && (
        <button className="button button--secondary typewriter__skip" type="button" onClick={handleSkip}>
          본문 바로 보기
        </button>
      )}
    </div>
  )
}

export default TypewriterText
