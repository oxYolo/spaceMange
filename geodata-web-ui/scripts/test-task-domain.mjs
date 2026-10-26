import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const temporary = await mkdtemp(join(tmpdir(), 'geodata-task-domain-'))
const tick = () => new Promise((resolve) => setTimeout(resolve, 0))
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

try {
  const entry = join(temporary, 'task-domain.mjs')
  await build({
    stdin: {
      contents: `export * from ${JSON.stringify(resolve('src/features/tasks/model.ts'))}; export { createPolling } from ${JSON.stringify(resolve('src/composables/usePolling.ts'))};`,
      resolveDir: process.cwd(),
    },
    outfile: entry, bundle: true, platform: 'node', format: 'esm', logLevel: 'silent',
  })
  const { taskStatus, taskProgress, canStoreTask, taskStored, taskDeletionMessage, lastTaskPage, createPolling } = await import(pathToFileURL(entry).href)

  assert.equal(taskStatus('0').key, 'run')
  assert.equal(taskStatus(1).key, 'done')
  assert.equal(taskStatus('2').key, 'fail')
  for (const value of [undefined, '', 'UNEXPECTED', '9']) assert.equal(taskStatus(value).key, 'unknown')
  assert.equal(taskStatus(' queued ').key, 'wait')
  assert.equal(taskStatus('已完成').key, 'done')
  assert.equal(taskProgress({ status: '0' }), null)
  assert.equal(taskProgress({ status: '0', progress: 'not-a-number' }), null)
  assert.equal(taskProgress({ status: '0', progress: Infinity }), null)
  assert.equal(taskProgress({ status: '0', progress: -5 }), 0)
  assert.equal(taskProgress({ status: '0', progress: 220 }), 100)
  assert.equal(taskProgress({ status: '1' }), 100)
  assert.equal(canStoreTask({ id: 'a', status: '1', origin: '7', storage: '0' }), true)
  for (const task of [
    { id: 'a', status: '0' }, { id: 'a', status: '2' }, { id: 'a', status: 'unknown' },
    { id: 'a', status: '1', origin: '10' }, { status: '1' }, { id: 'a', status: '1', storage: '1' },
  ]) assert.equal(canStoreTask(task), false)
  assert.equal(taskStored({ storage: true }), true)
  assert.match(taskDeletionMessage({ status: '0' }), /不会取消后台处理/)
  assert.match(taskDeletionMessage({ status: '1', storage: '1' }), /已入库的数据仍可/)
  assert.equal(lastTaskPage(10, 10), 1)
  assert.equal(lastTaskPage(11, 10), 2)
  assert.equal(lastTaskPage(0, 10), 1)

  let calls = 0
  let concurrent = 0
  let maximumConcurrent = 0
  const first = deferred()
  const second = deferred()
  const serial = createPolling(async () => {
    const index = ++calls
    maximumConcurrent = Math.max(maximumConcurrent, ++concurrent)
    await (index === 1 ? first.promise : second.promise)
    concurrent -= 1
  }, { interval: 5 })
  const original = serial.trigger()
  await tick()
  const joined = serial.trigger()
  serial.trigger()
  assert.equal(calls, 1, 'refreshes must not overlap')
  assert.equal(original, joined, 'refresh callers share the pending operation')
  first.resolve()
  await tick()
  assert.equal(calls, 2, 'multiple refreshes become one follow-up request')
  second.resolve()
  await original
  assert.equal(maximumConcurrent, 1)
  serial.dispose()

  let aborts = 0
  let errors = 0
  let visibilityCalls = 0
  const visibility = createPolling(async (signal) => {
    visibilityCalls += 1
    await new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => {
        aborts += 1
        reject(new Error('cancelled'))
      }, { once: true })
    })
  }, { interval: 5, onError: () => { errors += 1 } })
  visibility.start()
  await tick()
  visibility.setVisible(false)
  await tick()
  await delay(12)
  assert.equal(aborts, 1, 'hiding a page aborts its current read')
  assert.equal(visibilityCalls, 1, 'hidden pages do not poll')
  assert.equal(errors, 0, 'cancellation must be silent')
  visibility.setVisible(true)
  await tick()
  assert.equal(visibilityCalls, 2, 'visible pages refresh immediately')
  visibility.dispose()
  await tick()
  await delay(12)
  assert.equal(aborts, 2, 'disposal aborts the current read')
  assert.equal(visibilityCalls, 2, 'disposed polling cannot restart')
  visibility.start()
  await visibility.trigger()
  assert.equal(visibilityCalls, 2)

  let delayedCalls = 0
  const delayed = createPolling(async () => { delayedCalls += 1 }, { interval: 8 })
  delayed.start(false)
  assert.equal(delayedCalls, 0, 'delayed polling preserves the first interval')
  await delay(20)
  assert.ok(delayedCalls >= 1)
  delayed.stop()
  const stoppedCalls = delayedCalls
  await delay(20)
  assert.equal(delayedCalls, stoppedCalls, 'stopped polling does not reschedule')
  delayed.dispose()

  let disposedBeforeStart = 0
  const immediateDisposal = createPolling(async () => { disposedBeforeStart += 1 }, { interval: 5 })
  const beforeStart = immediateDisposal.trigger()
  immediateDisposal.dispose()
  await beforeStart
  assert.equal(disposedBeforeStart, 0)
  console.log('Task domain and polling tests passed: status/progress, storage eligibility, deletion/page recovery, serial refresh, visibility pause, cancellation, disposal.')
} finally {
  await rm(temporary, { recursive: true, force: true })
}
