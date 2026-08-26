function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * The inline markers, as data rather than four hand-written replaces.
 *
 * Two readers need this list — the HTML renderer and {@link toPlainText}, which strips markers for
 * consumers that cannot show them (a calendar app's DESCRIPTION field). A marker added to a chain
 * of `.replace` calls reaches whichever chain the author happened to edit; added here it reaches
 * both, so "strip" can no longer be the half somebody forgets.
 *
 * Order matters: `**` has to be consumed before `*` can see it.
 */
const INLINE_MARKERS: ReadonlyArray<readonly [marker: string, tag: string]> = [
  ['**', 'strong'],
  ['*', 'em'],
  ['__', 'u'],
  ['~~', 's'],
]

const INLINE_RULES = INLINE_MARKERS.map(([marker, tag]) => {
  const escaped = marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return { pattern: new RegExp(`${escaped}(.+?)${escaped}`, 'gs'), tag }
})

function applyInlineFormatting(escaped: string): string {
  return INLINE_RULES.reduce(
    (text, { pattern, tag }) => text.replace(pattern, `<${tag}>$1</${tag}>`),
    escaped,
  )
}

function stripInlineFormatting(text: string): string {
  return INLINE_RULES.reduce((acc, { pattern }) => acc.replace(pattern, '$1'), text)
}

/**
 * Bullets accept `•`, `-` and `*` — the toolbar writes `•`, but people type the other two.
 *
 * The trailing space is what keeps this off italics: `*ważne* dziś` has no space after the
 * marker, so it stays an inline emphasis rather than turning the line into a list item.
 */
const BULLET_RE = /^[•\-*] /
const NUMBERED_RE = /^\d+\. /
const LETTERED_RE = /^[a-z]\) /i
/**
 * One heading level, three accepted markers. `#`, `##` and `###` all render the same size on
 * purpose: a training plan needs sections, not a hierarchy, and offering three sizes only
 * raises the question of which one is bigger. Being forgiving about the marker costs nothing —
 * the same reason bullets take three characters.
 */
const HEADING_RE = /^#{1,3} /

const isBulletLine   = (l: string) => BULLET_RE.test(l)
const isNumberedLine = (l: string) => NUMBERED_RE.test(l)
const isLetteredLine = (l: string) => LETTERED_RE.test(l)
const isHeadingLine  = (l: string) => HEADING_RE.test(l)

const stripBullet   = (l: string) => l.replace(BULLET_RE, '')
const stripNumbered = (l: string) => l.replace(NUMBERED_RE, '')
const stripLettered = (l: string) => l.replace(LETTERED_RE, '')
const stripHeading  = (l: string) => l.replace(HEADING_RE, '')

/** A rendered chunk. `block` marks the ones that bring their own vertical rhythm. */
interface Part {
  html: string
  block: boolean
}

const inline = (line: string): string => applyInlineFormatting(escapeHtml(line))

/**
 * `start` for a list that does not begin at one.
 *
 * The typed marker is stripped and the browser draws the numbering itself, so without this a
 * list resumed after a heading ("3." "4.") silently renders as "1." "2." — the writer sees
 * numbers they did not write. Omitted at 1 so the common case stays plain markup.
 */
function startAttr(n: number): string {
  return Number.isSafeInteger(n) && n > 1 ? ` start="${n}"` : ''
}

/**
 * Converts pseudo-markdown content to safe HTML.
 *
 * Inline markers: **bold**  *italic*  __underline__  ~~strikethrough~~
 * Block markers (applied to line starts):
 *   • item     → <ul> bullet list (also `- item` and `* item`)
 *   1. item    → <ol> numbered list
 *   a) item    → <ol type="a"> lettered list
 *   ## Title   → section heading (also `#` and `###`)
 *
 * HTML entities are escaped first to prevent XSS. Callers holding text that the backend
 * escaped on write must decode it first (decodeHtmlEntities) — this function re-escapes,
 * so the round trip stays safe.
 */
export function renderRichText(content: string): string {
  // Split on CRLF too, and never re-emit a line break. The hosts render with white-space:
  // pre-wrap so a plan's indentation survives, and under pre-wrap a stray \r left at a line end
  // is a segment break of its own — on top of the <br> below it, text pasted from Windows would
  // come out double-spaced. No newline in the output means pre-wrap can only affect spaces.
  const lines = content.split(/\r?\n/)
  const parts: Part[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    if (isHeadingLine(line)) {
      // Inherits colour and scales with the surrounding text: this renderer serves the news
      // page, course blocks and the training modal, and each sets its own palette.
      parts.push({
        html: `<h4 class="font-semibold text-[1.0625em] mt-3 mb-1 first:mt-0">${inline(stripHeading(line))}</h4>`,
        block: true,
      })
      i++
      continue
    }

    if (isBulletLine(line)) {
      const items: string[] = []
      while (i < lines.length && isBulletLine(lines[i])) {
        items.push(`<li>${inline(stripBullet(lines[i]))}</li>`)
        i++
      }
      parts.push({ html: `<ul class="list-disc list-inside my-1 space-y-0.5">${items.join('')}</ul>`, block: true })
      continue
    }

    if (isNumberedLine(line)) {
      const from = startAttr(Number(NUMBERED_RE.exec(line)![0].slice(0, -2)))
      const items: string[] = []
      while (i < lines.length && isNumberedLine(lines[i])) {
        items.push(`<li>${inline(stripNumbered(lines[i]))}</li>`)
        i++
      }
      parts.push({ html: `<ol${from} class="list-decimal list-inside my-1 space-y-0.5">${items.join('')}</ol>`, block: true })
      continue
    }

    if (isLetteredLine(line)) {
      const from = startAttr(line.toLowerCase().charCodeAt(0) - 96)
      const items: string[] = []
      while (i < lines.length && isLetteredLine(lines[i])) {
        items.push(`<li>${inline(stripLettered(lines[i]))}</li>`)
        i++
      }
      parts.push({
        html: `<ol type="a"${from} class="list-[lower-alpha] list-inside my-1 space-y-0.5">${items.join('')}</ol>`,
        block: true,
      })
      continue
    }

    parts.push({ html: inline(line), block: false })
    i++
  }

  // A <br> separates two lines of running text — but a list or a heading already carries its own
  // margins, so wrapping one in breaks opens a gap twice the size of the one between paragraphs.
  return parts
    .map((part, idx) => (idx > 0 && !part.block && !parts[idx - 1].block ? '<br>' + part.html : part.html))
    .join('')
}

/**
 * The same text with every marker removed, for consumers that render no HTML at all.
 *
 * An event description is read in three places on the site AND handed to the reader's calendar
 * app, whose DESCRIPTION field is plain text — so markers that make the page readable are exactly
 * what turns "Co zabrać" into "## Co zabrać" on somebody's phone. Bullets survive as a character,
 * because a dash in front of an item still reads as a list once the markup is gone; numbers and
 * letters were already written out, so they need nothing.
 *
 * Kept beside the renderer on purpose: both read INLINE_MARKERS, so a marker cannot be taught to
 * one and not the other.
 */
export function toPlainText(content: string): string {
  return content
    .split(/\r?\n/)
    .map((line) => {
      const withoutBlock = isHeadingLine(line)
        ? stripHeading(line)
        : isBulletLine(line)
          ? `• ${stripBullet(line)}`
          : line
      return stripInlineFormatting(withoutBlock)
    })
    .join('\n')
}
