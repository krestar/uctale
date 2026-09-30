import { useEffect, useRef, useState } from 'react'
import { fetchGameImage } from '../api/gameApi'
import { isAccessAuthError } from '../api/apiError'
import {
  createGameImageState,
  isUsableImageBlob,
  shouldRequestGameImage,
} from './gameImageBehavior.js'

const GameImage = ({ src, alt, onAuthError, fallbackContent = null }) => {
  const [loadState, setLoadState] = useState(() => createGameImageState(src))
  const [isVisible, setIsVisible] = useState(false)
  const containerRef = useRef(null)
  const onAuthErrorRef = useRef(onAuthError)

  useEffect(() => {
    onAuthErrorRef.current = onAuthError
  }, [onAuthError])

  useEffect(() => {
    setLoadState(createGameImageState(src))
  }, [src])

  useEffect(() => {
    const element = containerRef.current
    if (!element) return undefined

    if (typeof IntersectionObserver === 'undefined') {
      setIsVisible(true)
      return undefined
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setIsVisible(true)
          observer.disconnect()
        }
      },
      { threshold: 0.01 },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    let cancelled = false
    let objectUrl = null

    if (!shouldRequestGameImage(src, isVisible)) return undefined

    setLoadState((current) => ({
      ...current,
      source: src,
      imageSrc: null,
      isLoading: true,
      hasError: false,
    }))

    fetchGameImage(src)
      .then((blob) => {
        if (cancelled) return
        if (!isUsableImageBlob(blob)) {
          throw new Error('empty image response')
        }
        objectUrl = URL.createObjectURL(blob)
        setLoadState({
          source: src,
          imageSrc: objectUrl,
          isLoading: false,
          hasError: false,
        })
      })
      .catch((error) => {
        if (cancelled) return
        if (isAccessAuthError(error) && onAuthErrorRef.current) {
          onAuthErrorRef.current(error)
          setLoadState((current) => ({ ...current, isLoading: false }))
          return
        }
        setLoadState({
          source: src,
          imageSrc: null,
          isLoading: false,
          hasError: true,
        })
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [src, isVisible])

  return (
    <div ref={containerRef} className="game-image" aria-busy={loadState.isLoading}>
      {loadState.isLoading && (
        <div className="game-image__status" role="status">
          <span className="spinner" aria-hidden="true" />
          <p>장면 이미지를 불러오고 있습니다.</p>
        </div>
      )}

      {loadState.hasError && !loadState.isLoading && (
        fallbackContent || (
          <p className="game-image__status game-image__status--error" role="status">
            장면 이미지를 불러오지 못했습니다. 이야기는 계속 진행할 수 있습니다.
          </p>
        )
      )}

      {loadState.imageSrc && <img className="game-image__media" src={loadState.imageSrc} alt={alt} />}
    </div>
  )
}

export default GameImage
