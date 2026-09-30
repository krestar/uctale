export function shouldRequestGameImage(src, hasEnteredViewport) {
  return Boolean(src && hasEnteredViewport)
}

export function isUsableImageBlob(blob) {
  return Boolean(blob && typeof blob.size === 'number' && blob.size > 0)
}

export function gameImageInstanceKey(src) {
  return src || '__empty-image__'
}
