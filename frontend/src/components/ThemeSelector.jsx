import { useEffect, useState } from 'react'

import {
  applyTheme,
  normalizeThemeMode,
  readThemeMode,
  resolveTheme,
  writeThemeMode,
} from '../theme/theme'

const OPTIONS = [
  { value: 'system', label: '시스템' },
  { value: 'light', label: '라이트' },
  { value: 'dark', label: '다크' },
]

function getInitialMode() {
  if (typeof window === 'undefined') return 'system'

  const bootstrappedMode = normalizeThemeMode(window.__UCTALE_INITIAL_THEME_MODE__)
  if (window.__UCTALE_INITIAL_THEME_MODE__) return bootstrappedMode

  return readThemeMode(window.localStorage)
}

function ThemeSelector() {
  const [mode, setMode] = useState(getInitialMode)

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const syncTheme = () => {
      applyTheme(document.documentElement, resolveTheme(mode, mediaQuery.matches))
    }

    syncTheme()

    if (mode !== 'system') return undefined

    mediaQuery.addEventListener('change', syncTheme)
    return () => mediaQuery.removeEventListener('change', syncTheme)
  }, [mode])

  const handleChange = (event) => {
    const nextMode = writeThemeMode(window.localStorage, event.target.value)
    setMode(nextMode)
  }

  return (
    <fieldset className="theme-selector" aria-label="화면 테마">
      <legend className="visually-hidden">화면 테마</legend>
      {OPTIONS.map((option) => (
        <label
          key={option.value}
          className={`theme-selector__option${mode === option.value ? ' is-selected' : ''}`}
        >
          <input
            type="radio"
            name="theme-mode"
            value={option.value}
            checked={mode === option.value}
            onChange={handleChange}
          />
          <span>{option.label}</span>
        </label>
      ))}
    </fieldset>
  )
}

export default ThemeSelector
