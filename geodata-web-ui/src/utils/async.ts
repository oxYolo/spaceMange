/** Ordered settled results with a bounded number of asynchronous operations. */
export async function mapSettledWithConcurrency<T, R>(
  items: readonly T[],
  concurrency: number,
  mapper: (item: T, index: number) => Promise<R>,
): Promise<PromiseSettledResult<R>[]> {
  const results: PromiseSettledResult<R>[] = new Array(items.length)
  let cursor = 0
  const limit = Math.max(1, Math.min(items.length, Math.trunc(concurrency) || 1))
  await Promise.all(Array.from({ length: limit }, async () => {
    while (cursor < items.length) {
      const index = cursor++
      try { results[index] = { status: 'fulfilled', value: await mapper(items[index], index) } }
      catch (reason) { results[index] = { status: 'rejected', reason } }
    }
  }))
  return results
}
