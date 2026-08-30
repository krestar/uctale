import assert from 'node:assert/strict'
import test from 'node:test'
import {
  advanceTypewriter,
  createTypewriterState,
  getDisplayedText,
  prefersReducedMotion,
  skipTypewriter,
} from './typewriterBehavior.js'

test('typewriter advances to natural completion exactly once in state', () => {
  let state = createTypewriterState('abc')
  assert.equal(state.isComplete, false)

  state = advanceTypewriter(state)
  assert.equal(getDisplayedText(state), 'a')
  assert.equal(state.isComplete, false)

  state = advanceTypewriter(state)
  state = advanceTypewriter(state)
  assert.equal(getDisplayedText(state), 'abc')
  assert.equal(state.isComplete, true)

  const completedState = advanceTypewriter(state)
  assert.equal(completedState, state)
})

test('typewriter skip completes immediately and remains complete', () => {
  const state = createTypewriterState('abcdef')
  const skipped = skipTypewriter(state)

  assert.equal(getDisplayedText(skipped), 'abcdef')
  assert.equal(skipped.isComplete, true)
  assert.equal(skipTypewriter(skipped), skipped)
})

test('creating state for new text resets displayed progress', () => {
  let state = createTypewriterState('first')
  state = advanceTypewriter(state)
  state = advanceTypewriter(state)
  assert.equal(getDisplayedText(state), 'fi')

  const next = createTypewriterState('second')
  assert.equal(getDisplayedText(next), '')
  assert.equal(next.index, 0)
  assert.equal(next.isComplete, false)
})

test('reduced motion resolves story immediately', () => {
  const state = createTypewriterState('full story', true)
  assert.equal(getDisplayedText(state), 'full story')
  assert.equal(state.isComplete, true)
})

test('reduced motion helper uses media query result', () => {
  assert.equal(prefersReducedMotion(() => ({ matches: true })), true)
  assert.equal(prefersReducedMotion(() => ({ matches: false })), false)
  assert.equal(prefersReducedMotion(undefined), false)
})
