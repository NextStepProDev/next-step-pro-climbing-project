/**
 * Ordering and merging the messages of a chat window. The window holds pieces from different
 * reads - the newest page, older pages loaded on scroll, new messages from polls, the message the
 * reader has just sent - and shows them as ONE list, in the server's order: (createdAt, id).
 */

export interface ChatMessage {
  id: string
  createdAt: string
}

/**
 * Compares two instants as the server writes them (`2026-11-25T15:04:48.123456Z`).
 *
 * ⚠️ Not a plain string comparison: the fraction has as many digits as it needs, so "…05.1Z" and
 * "…05.123456Z" do not line up character by character. And not `Date.parse`: that keeps
 * milliseconds, while the database keeps microseconds — two messages 300 µs apart would tie.
 */
export function compareInstants(a: string, b: string): number {
  const [aBase, aFraction] = splitInstant(a)
  const [bBase, bFraction] = splitInstant(b)
  if (aBase !== bBase) return aBase < bBase ? -1 : 1
  if (aFraction !== bFraction) return aFraction < bFraction ? -1 : 1
  return 0
}

function splitInstant(value: string): [string, string] {
  const trimmed = value.endsWith('Z') ? value.slice(0, -1) : value
  const dot = trimmed.indexOf('.')
  if (dot === -1) return [trimmed, '000000000']
  return [trimmed.slice(0, dot), trimmed.slice(dot + 1).padEnd(9, '0')]
}

/**
 * The server's order: time first, the id breaking a tie. Canonical lower-case UUID text compares
 * exactly as Postgres compares the uuid bytes, so this matches the paging cursor.
 */
export function compareChatMessages(a: ChatMessage, b: ChatMessage): number {
  return compareInstants(a.createdAt, b.createdAt) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)
}

/** One list from any number of pieces: each message once, in the server's order. */
export function mergeChatMessages<M extends ChatMessage>(...pieces: ReadonlyArray<readonly M[]>): M[] {
  const byId = new Map<string, M>()
  for (const piece of pieces) {
    for (const message of piece) {
      if (!byId.has(message.id)) byId.set(message.id, message)
    }
  }
  return [...byId.values()].sort(compareChatMessages)
}

/** The cursor for "anything new" when the window has nothing yet: before every message. */
export const BEFORE_EVERYTHING = { createdAt: '1970-01-01T00:00:00Z', id: '00000000-0000-0000-0000-000000000000' }
