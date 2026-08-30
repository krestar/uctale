import assert from 'node:assert/strict'
import test from 'node:test'

import {
  THEME_STORAGE_KEY,
  applyTheme,
  normalizeThemeMode,
  readThemeMode,
  resolveTheme,
  writeThemeMode,
} from './theme.js'

test('normalizeThemeMode falls back to system for unsupported values', () => {
  assert.equal(normalizeThemeMode('light'), 'light')
  assert.equal(normalizeThemeMode('dark'), 'dark')
  assert.equal(normalizeThemeMode('system'), 'system')
  assert.equal(normalizeThemeMode('sepia'), 'system')
  assert.equal(normalizeThemeMode(null), 'system')
})

test('resolveTheme follows the operating system only in system mode', () => {
  assert.equal(resolveTheme('system', true), 'dark')
  assert.equal(resolveTheme('system', false), 'light')
  assert.equal(resolveTheme('light', true), 'light')
  assert.equal(resolveTheme('dark', false), 'dark')
})

test('readThemeMode tolerates invalid values and storage failures', () => {
  const validStorage = {
    getItem: (key) => (key === THEME_STORAGE_KEY ? 'dark' : null),
  }
  const invalidStorage = {
    getItem: () => 'sepia',
  }
  const failingStorage = {
    getItem: () => {
      throw new Error('blocked')
    },
  }

  assert.equal(readThemeMode(validStorage), 'dark')
  assert.equal(readThemeMode(invalidStorage), 'system')
  assert.equal(readThemeMode(failingStorage), 'system')
})

test('writeThemeMode persists a normalized value without surfacing storage failures', () => {
  let savedValue = null
  const storage = {
    setItem: (key, value) => {
      assert.equal(key, THEME_STORAGE_KEY)
      savedValue = value
    },
  }

  assert.equal(writeThemeMode(storage, 'light'), 'light')
  assert.equal(savedValue, 'light')

  const failingStorage = {
    setItem: () => {
      throw new Error('blocked')
    },
  }

  assert.equal(writeThemeMode(failingStorage, 'invalid'), 'system')
})

test('applyTheme updates the root data-theme and native color scheme', () => {
  const root = { dataset: {}, style: {} }

  applyTheme(root, 'dark')
  assert.equal(root.dataset.theme, 'dark')
  assert.equal(root.style.colorScheme, 'dark')

  applyTheme(root, 'unknown')
  assert.equal(root.dataset.theme, 'light')
  assert.equal(root.style.colorScheme, 'light')
})
