export function createGameImageState(src) {
  return {
    source: src || null,
    imageSrc: null,
    isLoading: false,
    hasError: false,
  }
}

export function shouldRequestGameImage(src, isVisible) {
  return Boolean(src && isVisible)
}

export function isUsableImageBlob(blob) {
  return Boolean(blob && typeof blob.size === 'number' && blob.size > 0)
}
