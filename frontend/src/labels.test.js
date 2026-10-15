import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = new URL('.', import.meta.url)
const source = (name) => readFileSync(fileURLToPath(new URL(name, root)), 'utf8')

test('플레이어 화면의 주요 제목은 쉬운 한국어를 사용한다', () => {
  assert.match(source('screens/GamePlayScreen.jsx'), /현재 장면/)
  assert.match(source('components/GameRulesPanel.jsx'), /최근 행동 판정/)
  assert.match(source('components/GameRulesPanel.jsx'), /성공 기준/)
  assert.match(source('components/SessionLibrary.jsx'), /번째 장면/)
  assert.match(source('screens/AccessScreen.jsx'), /미리 체험하기/)
})
