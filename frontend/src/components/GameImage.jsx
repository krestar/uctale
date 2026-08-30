import { useEffect, useRef, useState } from 'react'
import { fetchGameImage } from '../api/gameApi'
import { isAccessAuthError } from '../api/apiError'

const GameImage = ({ src, alt, onAuthError }) => {
  const [imageSrc, setImageSrc] = useState(null)
  const [isLoading, setIsLoading] = useState(Boolean(src))
  const [hasError, setHasError] = useState(false)
  const onAuthErrorRef = useRef(onAuthError)

  useEffect(() => {
    onAuthErrorRef.current = onAuthError
  }, [onAuthError])

  useEffect(() => {
    let cancelled = false
    let objectUrl = null

    if (!src) return undefined

    fetchGameImage(src)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setImageSrc(objectUrl)
      })
      .catch((error) => {
        if (cancelled) return
        if (isAccessAuthError(error) && onAuthErrorRef.current) {
          onAuthErrorRef.current(error)
          return
        }
        setHasError(true)
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [src])

  return (
    <div className="game-image">
      {isLoading && (
        <div className="game-image__status">
          <span className="spinner" aria-hidden="true" />
          <p>AI 화가가 장면을 스케치하고 있습니다.</p>
        </div>
      )}

      {hasError && !isLoading && (
        <p className="game-image__status game-image__status--error">
          이미지를 불러오지 못했습니다.
        </p>
      )}

      {imageSrc && <img className="game-image__media" src={imageSrc} alt={alt} />}
    </div>
  )
}

export default GameImage
