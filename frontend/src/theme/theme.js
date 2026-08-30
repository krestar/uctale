export const THEME_STORAGE_KEY = 'uctale.theme-mode'

export const THEME_MODES = Object.freeze(['system', 'light', 'dark'])

export function normalizeThemeMode(value) {
  return THEME_MODES.includes(value) ? value : 'system'
}

export function resolveTheme(mode, prefersDark) {
  const normalizedMode = normalizeThemeMode(mode)

  if (normalizedMode === 'system') {
    return prefersDark ? 'dark' : 'light'
  }

  return normalizedMode
}

export function readThemeMode(storage) {
  try {
    return normalizeThemeMode(storage?.getItem(THEME_STORAGE_KEY))
  } catch {
    return 'system'
  }
}

export function writeThemeMode(storage, mode) {
  const normalizedMode = normalizeThemeMode(mode)

  try {
    storage?.setItem(THEME_STORAGE_KEY, normalizedMode)
  } catch {
    // Theme persistence is a convenience; storage failures must not block the game.
  }

  return normalizedMode
}

export function applyTheme(root, theme) {
  if (!root) return

  const resolvedTheme = theme === 'dark' ? 'dark' : 'light'
  root.dataset.theme = resolvedTheme
  root.style.colorScheme = resolvedTheme
}
