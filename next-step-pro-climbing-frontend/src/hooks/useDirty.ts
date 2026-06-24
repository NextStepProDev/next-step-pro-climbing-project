import { useState } from 'react'

// Serialize File objects by identity-relevant fields so swapping/selecting a
// file is detected as a change (plain JSON.stringify turns a File into `{}`).
function replacer(_key: string, value: unknown) {
  if (typeof File !== 'undefined' && value instanceof File) {
    return `File:${value.name}:${value.size}:${value.lastModified}`
  }
  return value
}

/**
 * Returns `true` when `current` differs from its value on first render.
 *
 * Snapshot-based: the hosting component must mount fresh for each edited
 * entity (e.g. `key={entity.id}` on the modal) so the baseline reflects the
 * entity's original values. Use to disable a "Save changes" button until the
 * user actually edits something: `disabled={!isDirty}`.
 */
export function useDirty<T>(current: T): boolean {
  const serialized = JSON.stringify(current, replacer)
  // Captured once on first render and never updated — the original baseline.
  const [initial] = useState(serialized)
  return serialized !== initial
}
