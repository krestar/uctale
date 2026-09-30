import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createGameImageState,
  isUsableImageBlob,
  shouldRequestGameImage,
} from './gameImageBehavior.js'

test('offscreen image is not requested until it becomes visible', () => {
  assert.equal(shouldRequestGameImage('/asset/1', false), false)
  assert.equal(shouldRequestGameImage('/asset/1', true), true)
  assert.equal(shouldRequestGameImage(null, true), false)
})

test('new src starts from a clean loading/error/object-url state', () => {
  const previous = {
    source: '/asset/old',
    imageSrc: 'blob:old',
    isLoading: false,
    hasError: true,
  }

  const next = createGameImageState('/asset/new')

  assert.equal(previous.hasError, true)
  assert.deepEqual(next, {
    source: '/asset/new',
    imageSrc: null,
    isLoading: false,
    hasError: false,
  })
})

test('empty thumbnail response is not treated as a usable image', () => {
  assert.equal(isUsableImageBlob({ size: 0 }), false)
  assert.equal(isUsableImageBlob({ size: 1 }), true)
  assert.equal(isUsableImageBlob(null), false)
})
