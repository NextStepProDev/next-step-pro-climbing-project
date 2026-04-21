export type BioBlock =
  | { type: 'text'; content: string }
  | { type: 'image'; url: string; alt: string }

const IMAGE_MD_RE = /^!\[([^\]]*)\]\((.+)\)$/

export function deserializeBio(bio: string): BioBlock[] {
  if (!bio.trim()) return []

  // JSON format (new)
  if (bio.trimStart().startsWith('[')) {
    try { return JSON.parse(bio) } catch { /* ignore parse errors, fall through to legacy */ }
  }

  // Legacy: plain text / markdown — auto-migrate to blocks
  const blocks: BioBlock[] = []
  let textBuffer: string[] = []

  const flushText = () => {
    const content = textBuffer.join('\n').trim()
    if (content) blocks.push({ type: 'text', content })
    textBuffer = []
  }

  for (const line of bio.split('\n')) {
    const match = line.trim().match(IMAGE_MD_RE)
    if (match) {
      flushText()
      blocks.push({ type: 'image', url: match[2], alt: match[1] })
    } else {
      textBuffer.push(line)
    }
  }
  flushText()
  return blocks
}

export function serializeBio(blocks: BioBlock[]): string {
  const filtered = blocks.filter(
    (b) => b.type === 'image' || (b.type === 'text' && b.content.trim()),
  )
  if (filtered.length === 0) return ''
  return JSON.stringify(filtered)
}
