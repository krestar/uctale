import { useEffect, useRef, useState } from 'react'
import { fetchGameImage } from '../api/gameApi'
import { isAccessAuthError } from '../api/apiError'
import {
  gameImageInstanceKey,
  isUsableImageBlob,
  shouldRequestGameImage,
} from './gameImageBehavior.js'

function GameImageContent({ src, alt, onAuthError, fallbackContent }) {
  const [imageSrc, setImageSrc] = useState(null)
  const [hasError, setHasError] = useState(false)
  const [hasEnteredViewport, setHasEnteredViewport] = useState(
    () => typeof IntersectionObserver === 'undefined',
  )
  const containerRef = useRef(null)
  const onAuthErrorRef = useRef(onAuthError)

  useEffect(() => {
    onAuthErrorRef.current = onAuthError
  }, [onAuthError])

  useEffect(() => {
    if (hasEnteredViewport) return undefined

    const element = containerRef.current
    if (!element) return undefined

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setHasEnteredViewport(true)
          observer.disconnect()
        }
      },
      { threshold: 0.01 },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [hasEnteredViewport])

  useEffect(() => {
    let cancelled = false
    let objectUrl = null

    if (!shouldRequestGameImage(src, hasEnteredViewport)) return undefined

    fetchGameImage(src)
      .then((blob) => {
        if (cancelled) return
        if (!isUsableImageBlob(blob)) {
          throw new Error('empty image response')
        }
        objectUrl = URL.createObjectURL(blob)
        setImageSrc(objectUrl)
      })
      .catch((error) => {
        if (cancelled) return
        if (isAccessAuthError(error) && onAuthErrorRef.current) {
          onAuthErrorRef.current(error)
        }
        setHasError(true)
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [src, hasEnteredViewport])

  const isLoading = shouldRequestGameImage(src, hasEnteredViewport) && !imageSrc && !hasError

  return (
    <div ref={containerRef} className="game-image" aria-busy={isLoading}>
      {isLoading && (
        <div className="game-image__status" role="status">
          <span className="spinner" aria-hidden="true" />
          <p>장면 이미지를 불러오고 있습니다.</p>
        </div>
      )}

      {hasError && (
        fallbackContent || (
          <p className="game-image__status game-image__status--error" role="status">
            장면 이미지를 불러오지 못했습니다. 이야기는 계속 진행할 수 있습니다.
          </p>
        )
      )}

      {imageSrc && <img className="game-image__media" src={imageSrc} alt={alt} />}
    </div>
  )
}

const GameImage = ({ src, alt, onAuthError, fallbackContent = null }) => (
  <GameImageContent
    key={gameImageInstanceKey(src)}
    src={src}
    alt={alt}
    onAuthError={onAuthError}
    fallbackContent={fallbackContent}
  />
)

export default GameImage
