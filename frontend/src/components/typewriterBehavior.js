export const createTypewriterState = (text, reducedMotion = false) => ({
  text,
  index: reducedMotion ? text.length : 0,
  isComplete: reducedMotion || text.length === 0,
})

export const advanceTypewriter = (state) => {
  if (state.isComplete) return state

  const nextIndex = Math.min(state.index + 1, state.text.length)
  return {
    ...state,
    index: nextIndex,
    isComplete: nextIndex >= state.text.length,
  }
}

export const skipTypewriter = (state) => {
  if (state.isComplete) return state
  return {
    ...state,
    index: state.text.length,
    isComplete: true,
  }
}

export const getDisplayedText = (state) => state.text.slice(0, state.index)

export const prefersReducedMotion = (matchMedia = globalThis.matchMedia) => {
  if (typeof matchMedia !== 'function') return false
  return matchMedia('(prefers-reduced-motion: reduce)').matches
}
