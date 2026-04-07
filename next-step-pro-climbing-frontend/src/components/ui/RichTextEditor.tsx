import { useRef } from 'react'
import { Bold, Italic, Underline, List, ListOrdered } from 'lucide-react'

interface RichTextEditorProps {
  value: string
  onChange: (value: string) => void
  rows?: number
  placeholder?: string
  className?: string
}

const LIST_PREFIX_RE = /^(• |\d+\. |[a-z]\) )/i

function stripListPrefix(line: string): string {
  return line.replace(LIST_PREFIX_RE, '')
}

export function RichTextEditor({
  value,
  onChange,
  rows = 10,
  placeholder,
  className,
}: RichTextEditorProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  /**
   * Replaces [selStart, selEnd) in the textarea with `text` using
   * document.execCommand so the browser's native undo stack is preserved.
   * Returns true on success, false if execCommand is unavailable (fallback).
   */
  const nativeInsert = (selStart: number, selEnd: number, text: string): boolean => {
    const ta = textareaRef.current
    if (!ta) return false
    ta.focus()
    ta.setSelectionRange(selStart, selEnd)
    return document.execCommand('insertText', false, text)
  }

  // Wraps selected characters with an inline marker (**bold**, *italic*, __underline__)
  const wrapSelection = (marker: string) => {
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end   = ta.selectionEnd
    const selected = ta.value.slice(start, end)
    const replacement = marker + selected + marker

    if (nativeInsert(start, end, replacement)) {
      // Restore inner selection (the text between markers)
      ta.setSelectionRange(start + marker.length, start + marker.length + selected.length)
    } else {
      // Fallback: manual state update
      onChange(value.slice(0, start) + replacement + value.slice(end))
      requestAnimationFrame(() => {
        ta?.focus()
        ta?.setSelectionRange(start + marker.length, start + marker.length + selected.length)
      })
    }
  }

  // Applies/toggles list prefixes on all lines covered by the current selection
  const applyList = (type: 'bullet' | 'numbered' | 'lettered') => {
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end   = ta.selectionEnd

    // Expand to full line boundaries
    const lineStart  = ta.value.lastIndexOf('\n', start - 1) + 1
    const lineEndIdx = ta.value.indexOf('\n', end)
    const lineEnd    = lineEndIdx === -1 ? ta.value.length : lineEndIdx

    const lines = ta.value.slice(lineStart, lineEnd).split('\n')

    const allBullet   = lines.every(l => l.startsWith('• '))
    const allNumbered = lines.every(l => /^\d+\. /.test(l))
    const allLettered = lines.every(l => /^[a-z]\) /i.test(l))

    let newLines: string[]
    if (type === 'bullet') {
      newLines = allBullet
        ? lines.map(stripListPrefix)
        : lines.map(l => `• ${stripListPrefix(l)}`)
    } else if (type === 'numbered') {
      newLines = allNumbered
        ? lines.map(stripListPrefix)
        : lines.map((l, i) => `${i + 1}. ${stripListPrefix(l)}`)
    } else {
      newLines = allLettered
        ? lines.map(stripListPrefix)
        : lines.map((l, i) => `${String.fromCharCode(97 + i)}) ${stripListPrefix(l)}`)
    }

    const newSelected = newLines.join('\n')
    if (nativeInsert(lineStart, lineEnd, newSelected)) {
      ta.setSelectionRange(lineStart, lineStart + newSelected.length)
    } else {
      // Fallback
      onChange(ta.value.slice(0, lineStart) + newSelected + ta.value.slice(lineEnd))
      requestAnimationFrame(() => {
        ta?.focus()
        ta?.setSelectionRange(lineStart, lineStart + newSelected.length)
      })
    }
  }

  const btn = (onAction: () => void, title: string, children: React.ReactNode) => (
    <button
      type="button"
      onMouseDown={(e) => { e.preventDefault(); onAction() }}
      className="p-1 rounded text-dark-300 hover:text-dark-100 hover:bg-dark-700 transition-colors"
      title={title}
    >
      {children}
    </button>
  )

  return (
    <div className={className}>
      <div className="flex items-center gap-0.5 border border-dark-600 border-b-0 rounded-t bg-dark-800 px-2 py-1">
        {btn(() => wrapSelection('**'), 'Pogrubienie', <Bold className="h-3.5 w-3.5" />)}
        {btn(() => wrapSelection('*'),  'Kursywa',     <Italic className="h-3.5 w-3.5" />)}
        {btn(() => wrapSelection('__'), 'Podkreślenie', <Underline className="h-3.5 w-3.5" />)}
        <span className="w-px h-4 bg-dark-600 mx-1.5" />
        {btn(() => applyList('bullet'),   'Lista punktowana (•)',  <List className="h-3.5 w-3.5" />)}
        {btn(() => applyList('numbered'), 'Lista numerowana (1.)', <ListOrdered className="h-3.5 w-3.5" />)}
        {btn(() => applyList('lettered'), 'Lista literowa (a))',
          <span className="text-xs font-mono leading-none px-0.5">a)</span>
        )}
        <span className="ml-auto text-xs text-dark-500 hidden sm:block">Zaznacz tekst → kliknij styl</span>
      </div>
      <textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={rows}
        placeholder={placeholder}
        className="w-full bg-dark-700 border border-dark-600 rounded-b px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 resize-y text-sm"
      />
    </div>
  )
}
