import assert from 'node:assert/strict'
import test from 'node:test'
import { createAsyncRequestGate, progressRequestKey } from './asyncRequestGate.js'

test('duplicate progress uses one active operation even before React state commits', () => {
  const gate = createAsyncRequestGate()
  const first = gate.begin('progress')
  assert.equal(first, 0)
  assert.equal(gate.begin('progress'), null)
  gate.end('progress', first)
  assert.equal(gate.begin('progress'), first)
})

test('duplicate resume requests are suppressed until the first completes', () => {
  const gate = createAsyncRequestGate()
  const first = gate.begin('resume')
  assert.equal(gate.begin('resume'), null)
  gate.end('resume', first)
  assert.equal(gate.begin('resume'), first)
})

test('reauthentication invalidates stale async responses and releases locks', () => {
  const gate = createAsyncRequestGate()
  const stale = gate.begin('progress')
  gate.invalidate()
  const current = gate.begin('progress')
  assert.equal(gate.isCurrent(stale), false)
  assert.equal(gate.isCurrent(current), true)
  gate.end('progress', stale)
  assert.equal(gate.begin('progress'), null)
  gate.end('progress', current)
  assert.equal(gate.begin('progress'), current)
})

test('retry sends the identical idempotency key while a new choice gets a new key', () => {
  let generated = 0
  const create = () => 'key-' + ++generated
  const first = progressRequestKey(null, create)
  const retry = progressRequestKey(first, create)
  assert.equal(retry, first)
  assert.equal(generated, 1)
  assert.equal(progressRequestKey(null, create), 'key-2')
})
