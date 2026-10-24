/**
 * Unsaved writing kept on this device, so a navbar click, a back gesture, a refresh or a tab
 * that iOS threw out of memory does not cost the text.
 *
 * Every key carries the owner's id. These drafts hold training plans and thread messages —
 * health data (GDPR art. 9) on a device that may be shared — so a draft is readable only by its
 * owner, every draft goes on an explicit logout, a login purges everybody else's, and nothing
 * outlives {@link DRAFT_TTL_MS}. An expired session does NOT clear them: that is exactly the
 * moment the text has nowhere else to live.
 *
 * Storage can throw (private mode, quota, blocked site data); every access is wrapped, and a
 * draft that cannot be kept is simply not kept — the editor works exactly as before.
 */

const PREFIX = 'nsp_draft:'
export const DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000

export interface StoredDraft<T> {
  savedAt: number
  /**
   * The version of the saved entity the writing started from (its `updatedAt`), or null for
   * something not yet saved. A different baseline today means someone saved in between, and
   * restoring would overwrite their change with older text.
   */
  baseline: string | null
  data: T
}

function storageKey(userId: string, scope: string): string {
  return `${PREFIX}${userId}:${scope}`
}

function ownerOf(key: string): string | null {
  if (!key.startsWith(PREFIX)) return null
  const rest = key.slice(PREFIX.length)
  const colon = rest.indexOf(':')
  return colon > 0 ? rest.slice(0, colon) : null
}

function draftKeys(): string[] {
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith(PREFIX)) keys.push(key)
    }
    return keys
  } catch {
    return []
  }
}

function removeKey(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // Nothing to do: storage we cannot write is storage that holds nothing of ours.
  }
}

export function readDraft<T>(userId: string, scope: string, now = Date.now()): StoredDraft<T> | null {
  const key = storageKey(userId, scope)
  try {
    const raw = localStorage.getItem(key)
    if (raw === null) return null
    const parsed = JSON.parse(raw) as StoredDraft<T>
    if (typeof parsed?.savedAt !== 'number' || now - parsed.savedAt > DRAFT_TTL_MS) {
      removeKey(key)
      return null
    }
    return parsed
  } catch {
    removeKey(key)
    return null
  }
}

export function writeDraft<T>(userId: string, scope: string, draft: StoredDraft<T>): void {
  try {
    localStorage.setItem(storageKey(userId, scope), JSON.stringify(draft))
  } catch {
    // Quota or blocked storage: the draft is a safety net, never a reason to break the editor.
  }
}

export function removeDraft(userId: string, scope: string): void {
  removeKey(storageKey(userId, scope))
}

/** Explicit logout: the device may be handed to someone else next. */
export function clearAllDrafts(): void {
  draftKeys().forEach(removeKey)
}

/** On login: drop every other account's drafts and anything past its life. */
export function pruneDrafts(currentUserId: string, now = Date.now()): void {
  for (const key of draftKeys()) {
    if (ownerOf(key) !== currentUserId) {
      removeKey(key)
      continue
    }
    try {
      const parsed = JSON.parse(localStorage.getItem(key) ?? 'null') as StoredDraft<unknown> | null
      if (typeof parsed?.savedAt !== 'number' || now - parsed.savedAt > DRAFT_TTL_MS) removeKey(key)
    } catch {
      removeKey(key)
    }
  }
}
