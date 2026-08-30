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
    completionNotifiedRef.current = false
    setState(createTypewriterState(text, prefersReducedMotion()))
  }, [text])

  useEffect(() => {
    if (state.text !== text) return undefined

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
  }, [state, speed, text])

  const handleSkip = () => {
    setState((current) => skipTypewriter(current))
  }

  return (
    <div className="typewriter">
      <p className="sr-only">{text}</p>
      <p className="story-copy" aria-hidden="true">{state.text === text ? getDisplayedText(state) : ''}</p>
      {state.text === text && !state.isComplete && (
        <button className="button button--secondary typewriter__skip" type="button" onClick={handleSkip}>
          본문 바로 보기
        </button>
      )}
    </div>
  )
}

export default TypewriterText
