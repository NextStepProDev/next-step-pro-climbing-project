function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function applyInlineFormatting(escaped: string): string {
  return escaped
    .replace(/\*\*(.+?)\*\*/gs, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/gs, '<em>$1</em>')
    .replace(/__(.+?)__/gs, '<u>$1</u>')
}

const isBulletLine  = (l: string) => l.startsWith('• ')
const isNumberedLine = (l: string) => /^\d+\.\s/.test(l)
const isLetteredLine = (l: string) => /^[a-z]\)\s/i.test(l)

const stripBullet   = (l: string) => l.slice(2)
const stripNumbered = (l: string) => l.replace(/^\d+\.\s/, '')
const stripLettered = (l: string) => l.replace(/^[a-z]\)\s/i, '')

/**
 * Converts pseudo-markdown content to safe HTML.
 *
 * Inline markers: **bold**  *italic*  __underline__
 * Block markers (applied to line starts):
 *   • item     → <ul> bullet list
 *   1. item    → <ol> numbered list
 *   a) item    → <ol type="a"> lettered list
 *
 * HTML entities are escaped first to prevent XSS.
 */
export function renderRichText(content: string): string {
  const lines = content.split('\n')
  const parts: string[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    if (isBulletLine(line)) {
      const items: string[] = []
      while (i < lines.length && isBulletLine(lines[i])) {
        items.push(`<li>${applyInlineFormatting(escapeHtml(stripBullet(lines[i])))}</li>`)
        i++
      }
      parts.push(`<ul class="list-disc list-inside my-1 space-y-0.5">${items.join('')}</ul>`)
      continue
    }

    if (isNumberedLine(line)) {
      const items: string[] = []
      while (i < lines.length && isNumberedLine(lines[i])) {
        items.push(`<li>${applyInlineFormatting(escapeHtml(stripNumbered(lines[i])))}</li>`)
        i++
      }
      parts.push(`<ol class="list-decimal list-inside my-1 space-y-0.5">${items.join('')}</ol>`)
      continue
    }

    if (isLetteredLine(line)) {
      const items: string[] = []
      while (i < lines.length && isLetteredLine(lines[i])) {
        items.push(`<li>${applyInlineFormatting(escapeHtml(stripLettered(lines[i])))}</li>`)
        i++
      }
      parts.push(`<ol type="a" class="list-[lower-alpha] list-inside my-1 space-y-0.5">${items.join('')}</ol>`)
      continue
    }

    parts.push(applyInlineFormatting(escapeHtml(line)))
    i++
  }

  return parts.join('<br>')
}
