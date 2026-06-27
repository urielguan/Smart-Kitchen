import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const filePath = path.resolve('/Users/guanyiru/Desktop/yingzicode/yingzi-zncf/kitchen-frontend/src/api/modules/cook.ts')
const source = fs.readFileSync(filePath, 'utf8')

const requiredMarkers = [
  '${MOCK_FLOW_GROUP}-PENDING',
  '${MOCK_FLOW_GROUP}-COOKING',
  '${MOCK_FLOW_GROUP}-REVIEW',
  '${MOCK_FLOW_GROUP}-ARCHIVED'
]

test('mock chef flow orders cover the full execution lifecycle', () => {
  for (const marker of requiredMarkers) {
    assert.equal(source.includes(marker), true, `missing mock flow marker: ${marker}`)
  }

  assert.match(source, /status:\s*'pending'/, 'missing pending status sample')
  assert.match(source, /status:\s*'in_progress'/, 'missing in-progress status sample')
  assert.match(source, /status:\s*'completed'/, 'missing completed status sample')
  assert.match(source, /status:\s*'archived'/, 'missing archived status sample')
})

test('mock chef flow contains review and handoff states', () => {
  assert.match(source, /reviewStatus:\s*'pending_review'/, 'missing pending review sample')
  assert.match(source, /reviewStatus:\s*'approved'/, 'missing approved review sample')
  assert.match(source, /handoffStatus:\s*'pending'/, 'missing pending handoff sample')
  assert.match(source, /handoffStatus:\s*'ready'/, 'missing ready handoff sample')
})

test('mock chef flow includes realistic task guidance copy', () => {
  assert.match(source, /厨师流程演练：第 1 步待开始/, 'missing pending guidance copy')
  assert.match(source, /厨师流程演练：第 2 步进行中/, 'missing in-progress guidance copy')
  assert.match(source, /厨师流程演练：第 3 步完成待复核/, 'missing review guidance copy')
  assert.match(source, /厨师流程演练：第 4 步已归档/, 'missing archived guidance copy')
})

test('mock chef flow includes explicit pending, cooking, handoff, and urgent reminder labels', () => {
  assert.match(source, /待执行场景/, 'missing pending scenario label')
  assert.match(source, /进行中场景/, 'missing in-progress scenario label')
  assert.match(source, /待交接场景/, 'missing handoff scenario label')
  assert.match(source, /紧急提醒场景/, 'missing urgent reminder scenario label')
})

test('mock chef flow is prioritized to appear on the first screen', () => {
  assert.match(source, /const sortMockTasksForDisplay = \(tasks: MockCookTaskRecord\[\]\)/, 'missing display sort helper')
  assert.match(source, /task\.taskNo\.includes\(MOCK_FLOW_GROUP\)/, 'missing flow task priority rule')
  assert.match(source, /task\.taskNo\.includes\('PENDING'\)/, 'missing pending priority rule')
  assert.match(source, /task\.taskNo\.includes\('COOKING'\)/, 'missing cooking priority rule')
  assert.match(source, /task\.taskNo\.includes\('REVIEW'\)/, 'missing review priority rule')
  assert.match(source, /task\.taskNo\.includes\('ARCHIVED'\)/, 'missing archived priority rule')
})
