/**
 * Typing behaviour for the pseudo-markdown fields (see renderRichText for the output side).
 *
 * These are pure functions over (text, caret) rather than DOM handlers, because the same rules
 * have to run in two places that disagree about which key inserts a newline: in the big editors
 * that is Enter, in the 1:1 comment thread Enter sends the message and Shift+Enter breaks the
 * line. A helper that assumed the key would only fit one of them.
 */

export const BULLET_MARKER = '• '

/** Matches every list prefix the editor writes or accepts, including hand-typed `- ` and `* `. */
export const LIST_PREFIX_RE = /^(• |- |\* |\d+\. |[a-z]\) )/i

/** The result of a rule that fired: the whole new field value plus where the caret belongs. */
export interface TextEdit {
  value: string
  caret: number
}

function lineStartAt(value: string, caret: number): number {
  return value.lastIndexOf('\n', caret - 1) + 1
}

function lineEndAt(value: string, caret: number): number {
  const idx = value.indexOf('\n', caret)
  return idx === -1 ? value.length : idx
}

/** `2. ` → `3. `, `b) ` → `c) `, bullets stay bullets. */
function nextPrefix(prefix: string): string {
  const numbered = /^(\d+)\. $/.exec(prefix)
  if (numbered) return `${Number(numbered[1]) + 1}. `

  const lettered = /^([a-z])\) $/i.exec(prefix)
  if (lettered) {
    const code = lettered[1].toLowerCase().charCodeAt(0)
    // 'z' has nowhere to go; repeating it beats wrapping to '{'
    return code >= 122 ? lettered[0] : `${String.fromCharCode(code + 1)}) `
  }

  return BULLET_MARKER
}

/**
 * What a newline should do inside a list.
 *
 * On a list item with text → opens the next item already numbered. On an empty item → drops the
 * marker instead, which is how you leave a list without reaching for the toolbar.
 *
 * Returns null when the caret is not on a list line, or when text is selected — the caller then
 * lets the key do whatever it normally does.
 *
 * ⚠️ `maxLength` is not optional in spirit: the marker is inserted PROGRAMMATICALLY, and the
 * textarea's own maxLength only constrains typing. Without the check, Enter near the limit pushes
 * the field two characters over one that every request DTO enforces with @Size — so the write
 * comes back 400 on a field whose length the writer was never shown. Declining here instead lets
 * the plain newline through, which the browser blocks at the limit like any other keystroke.
 */
export function continueList(
  value: string,
  selectionStart: number,
  selectionEnd = selectionStart,
  maxLength?: number,
): TextEdit | null {
  if (selectionStart !== selectionEnd) return null

  const start = lineStartAt(value, selectionStart)
  const line = value.slice(start, lineEndAt(value, selectionStart))
  const match = LIST_PREFIX_RE.exec(line)
  if (!match) return null

  const prefix = match[0]
  const content = line.slice(prefix.length)

  if (content.trim() === '') {
    // Leave the list: strip the marker, keep the (empty) line the caret is already on.
    return { value: value.slice(0, start) + value.slice(start + prefix.length), caret: start }
  }

  const inserted = '\n' + nextPrefix(prefix)
  if (maxLength != null && value.length + inserted.length > maxLength) return null

  return {
    value: value.slice(0, selectionStart) + inserted + value.slice(selectionStart),
    caret: selectionStart + inserted.length,
  }
}

/**
 * Turns a just-typed `- ` or `* ` at the start of a line into the bullet the renderer and the
 * toolbar both use.
 *
 * Normalising rather than teaching every consumer to accept three markers keeps one document
 * from mixing them — the paste converter and the toolbar already write `•`. The renderer still
 * accepts the raw characters, for text that arrived from somewhere else.
 *
 * Fires only on the exact keystroke that completed the marker (caret sits right after the
 * space), so `3 - 4 serie` mid-line is untouched.
 */
export function normalizeBulletMarker(value: string, caret: number): TextEdit | null {
  const start = lineStartAt(value, caret)
  if (caret !== start + 2) return null

  const typed = value.slice(start, caret)
  if (typed !== '- ' && typed !== '* ') return null

  return {
    value: value.slice(0, start) + BULLET_MARKER + value.slice(caret),
    caret: start + BULLET_MARKER.length,
  }
}
