// Authentication changes invalidate every outstanding session / mutation response.
export function createAsyncRequestGate() {
  let generation = 0
  const active = new Set()

  return {
    epoch: () => generation,
    isCurrent: (epoch) => generation === epoch,
    begin: (operation) => {
      if (active.has(operation)) return null
      active.add(operation)
      return generation
    },
    end: (operation, epoch) => {
      if (generation === epoch) active.delete(operation)
    },
    invalidate: () => {
      generation += 1
      active.clear()
    },
  }
}

export function progressRequestKey(retryKey, createKey) {
  return retryKey || createKey()
}
