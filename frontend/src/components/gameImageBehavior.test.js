import assert from 'node:assert/strict'
import test from 'node:test'
import {
  gameImageInstanceKey,
  isUsableImageBlob,
  shouldRequestGameImage,
} from './gameImageBehavior.js'

test('offscreen image is not requested until it becomes visible', () => {
  assert.equal(shouldRequestGameImage('/asset/1', false), false)
  assert.equal(shouldRequestGameImage('/asset/1', true), true)
  assert.equal(shouldRequestGameImage(null, true), false)
})

test('src change creates a distinct image instance so stale error and object URL state cannot survive', () => {
  assert.notEqual(gameImageInstanceKey('/asset/old'), gameImageInstanceKey('/asset/new'))
  assert.equal(gameImageInstanceKey(null), '__empty-image__')
})

test('empty thumbnail response is not treated as a usable image', () => {
  assert.equal(isUsableImageBlob({ size: 0 }), false)
  assert.equal(isUsableImageBlob({ size: 1 }), true)
  assert.equal(isUsableImageBlob(null), false)
})
