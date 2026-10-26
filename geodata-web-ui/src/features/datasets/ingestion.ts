import type { UnifiedRow } from './model'

/** Table names and uploadStatus are assigned before SHP import finishes. */
export function layerIngestionReady(value: any): boolean {
  return String(value?.publishStatus ?? '') === '1' || String(value?.isShow ?? value?.IsShow ?? '') === '1'
}

function isVectorLayer(row: UnifiedRow): boolean {
  return row.src === 'layer' && (row.typeKey === 'vector' || row.typeKey === 'white')
}

/** List DTOs omit isShow, so unpublished vectors need an authoritative detail read. */
export function needsIngestionVerification(row: UnifiedRow): boolean {
  return isVectorLayer(row) && !row.published && !row.ingestReady && !layerIngestionReady(row.raw)
}

/** A missing or not-yet-visible result stays pending; it is not evidence of failure. */
export function applyIngestionDetail(row: UnifiedRow, detail: unknown): UnifiedRow {
  if (!isVectorLayer(row)) return row
  const value = detail && typeof detail === 'object' && !Array.isArray(detail) ? detail as Record<string, unknown> : {}
  // A mismatched response must never authorize operations on a different layer.
  const valid = value.id === undefined || String(value.id) === row.id
  const raw = valid ? { ...row.raw, ...value } : { ...row.raw }
  const published = row.published || String(raw.publishStatus ?? '') === '1'
  const ready = published || layerIngestionReady(raw)
  return {
    ...row, published, ingestReady: ready, ingestPending: !ready,
    status: ready ? 'ok' : 'uploading', statusLabel: ready ? '已入库' : '入库待确认',
    progress: ready ? 100 : 0,
    raw: { ...raw, ingestReady: ready, ingestPending: !ready },
  }
}
