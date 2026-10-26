import { onBeforeUnmount, onMounted, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'

interface PollingOptions {
  interval: number | (() => number)
  onError?: (error: unknown) => void
  onRunningChange?: (running: boolean) => void
}

/** Serial polling controller. A requested refresh during a request is coalesced into one follow-up. */
export function createPolling(task: (signal: AbortSignal) => Promise<void>, options: PollingOptions) {
  let active = false
  let visible = true
  let disposed = false
  let queued = false
  let controller: AbortController | undefined
  let timer: ReturnType<typeof setTimeout> | undefined
  let pending: Promise<void> | undefined

  function clearTimer() {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  function schedule() {
    clearTimer()
    if (!active || !visible || disposed || pending) return
    const interval = typeof options.interval === 'function' ? options.interval() : options.interval
    timer = setTimeout(() => { void trigger() }, Math.max(1, interval))
  }

  function trigger(): Promise<void> {
    if (disposed || !visible) return Promise.resolve()
    if (pending) {
      queued = true
      return pending
    }
    clearTimer()
    options.onRunningChange?.(true)
    // Defer the task until pending is assigned, including when task throws synchronously.
    pending = Promise.resolve().then(async () => {
      if (disposed || !visible) return
      do {
        queued = false
        controller = new AbortController()
        try {
          await task(controller.signal)
        } catch (error) {
          if (!controller.signal.aborted && !disposed) options.onError?.(error)
        }
      } while (queued && visible && !disposed)
    }).finally(() => {
      controller = undefined
      pending = undefined
      options.onRunningChange?.(false)
      schedule()
    })
    return pending
  }

  function start(immediate = true) {
    if (disposed) return
    active = true
    if (immediate) void trigger()
    else schedule()
  }

  function stop() {
    active = false
    queued = false
    clearTimer()
    controller?.abort()
  }

  function setVisible(value: boolean) {
    const wasVisible = visible
    visible = value
    if (!visible) {
      clearTimer()
      queued = false
      controller?.abort()
    } else if (!wasVisible && active) {
      void trigger()
    }
  }

  function dispose() {
    disposed = true
    stop()
  }

  return { trigger, start, stop, setVisible, dispose }
}

export interface UsePollingOptions extends Omit<PollingOptions, 'onRunningChange'> {
  enabled?: MaybeRefOrGetter<boolean>
  /** Whether mounting / enabling should request data immediately. Defaults to true. */
  immediate?: boolean
}

/** Pauses polling in hidden tabs and aborts the current request when stopped or unmounted. */
export function usePolling(task: (signal: AbortSignal) => Promise<void>, options: UsePollingOptions) {
  const running = ref(false)
  const polling = createPolling(task, { ...options, onRunningChange: (value) => { running.value = value } })
  let mounted = false
  const updateVisibility = () => polling.setVisible(document.visibilityState !== 'hidden')

  watch(() => toValue(options.enabled ?? true), (enabled) => {
    if (!mounted) return
    if (enabled) polling.start(options.immediate !== false)
    else polling.stop()
  })

  onMounted(() => {
    mounted = true
    updateVisibility()
    document.addEventListener('visibilitychange', updateVisibility)
    if (toValue(options.enabled ?? true)) polling.start(options.immediate !== false)
  })

  onBeforeUnmount(() => {
    mounted = false
    document.removeEventListener('visibilitychange', updateVisibility)
    polling.dispose()
  })

  return { running, trigger: polling.trigger, start: polling.start, stop: polling.stop }
}
