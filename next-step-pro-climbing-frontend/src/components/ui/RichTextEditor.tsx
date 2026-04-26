import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from 'react'
import { Bold, Italic, Underline, List, ListOrdered } from 'lucide-react'

function htmlToRichText(html: string): string | null {
  const doc = new DOMParser().parseFromString(html, 'text/html')

  function convert(node: Node): string {
    if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
    if (node.nodeType !== Node.ELEMENT_NODE) return ''

    const el = node as Element
    const tag = el.tagName.toLowerCase()

    if (tag === 'ul') {
      return Array.from(el.children)
        .filter(c => c.tagName.toLowerCase() === 'li')
        .map(li => `• ${convertChildren(li).trim()}`)
        .join('\n') + '\n'
    }

    if (tag === 'ol') {
      return Array.from(el.children)
        .filter(c => c.tagName.toLowerCase() === 'li')
        .map((li, i) => `${i + 1}. ${convertChildren(li).trim()}`)
        .join('\n') + '\n'
    }

    const inner = convertChildren(el)
    switch (tag) {
      case 'strong': case 'b': return `**${inner.trim()}**`
      case 'em':     case 'i': return `*${inner.trim()}*`
      case 'u':                return `__${inner.trim()}__`
      case 'br':               return '\n'
      case 'p': case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6':
        return inner.trim() ? inner.trim() + '\n' : ''
      case 'div':
        return inner.trim() ? inner.trim() + '\n' : ''
      default: return inner
    }
  }

  function convertChildren(el: Element): string {
    return Array.from(el.childNodes).map(convert).join('')
  }

  const result = convertChildren(doc.body).replace(/\n{3,}/g, '\n\n').trim()
  return result || null
}

interface RichTextEditorProps {
  value: string
  onChange: (value: string) => void
  rows?: number
  placeholder?: string
  className?: string
}

interface ToolbarButtonProps {
  onAction: () => void
  title: string
  children: React.ReactNode
}

function ToolbarButton({ onAction, title, children }: ToolbarButtonProps) {
  return (
    <button
      type="button"
      onMouseDown={(e) => { e.preventDefault(); onAction() }}
      className="p-1 rounded text-dark-300 hover:text-dark-100 hover:bg-dark-700 transition-colors"
      title={title}
    >
      {children}
    </button>
  )
}

const LIST_PREFIX_RE = /^(• |\d+\. |[a-z]\) )/i

function stripListPrefix(line: string): string {
  return line.replace(LIST_PREFIX_RE, '')
}

export const RichTextEditor = forwardRef<HTMLTextAreaElement, RichTextEditorProps>(function RichTextEditor({
  value,
  onChange,
  rows = 16,
  placeholder,
  className,
}, forwardedRef) {
  const internalRef = useRef<HTMLTextAreaElement>(null)
  useImperativeHandle(forwardedRef, () => internalRef.current!, [])
  const textareaRef = internalRef

  // ─── Undo / Redo history ────────────────────────────────────────────────────
  const history          = useRef<string[]>([value])
  const histIdx          = useRef(0)
  const isUndoRedo       = useRef(false)
  const isInternalChange = useRef(false)   // set before every internal onChange call

  // Reset history only when value changes externally (parent resets the form, etc.)
  useEffect(() => {
    if (isUndoRedo.current || isInternalChange.current) {
      isUndoRedo.current       = false
      isInternalChange.current = false
      return
    }
    // Genuine external change — start fresh
    history.current = [value]
    histIdx.current = 0
  }, [value])

  const recordHistory = useCallback((newVal: string) => {
    const slice = history.current.slice(0, histIdx.current + 1)
    if (slice[slice.length - 1] === newVal) return
    history.current = [...slice, newVal]
    histIdx.current = history.current.length - 1
  }, [])

  const callOnChange = useCallback((newVal: string) => {
    isInternalChange.current = true
    recordHistory(newVal)
    onChange(newVal)
  }, [onChange, recordHistory])

  const undo = useCallback(() => {
    if (histIdx.current === 0) return
    histIdx.current -= 1
    isUndoRedo.current = true
    onChange(history.current[histIdx.current])
  }, [onChange])

  const redo = useCallback(() => {
    if (histIdx.current >= history.current.length - 1) return
    histIdx.current += 1
    isUndoRedo.current = true
    onChange(history.current[histIdx.current])
  }, [onChange])
  // ───────────────────────────────────────────────────────────────────────────

  const wrapSelection = useCallback((marker: string) => {
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end   = ta.selectionEnd
    const selected = ta.value.slice(start, end)
    const replacement = marker + selected + marker
    const newVal = ta.value.slice(0, start) + replacement + ta.value.slice(end)
    callOnChange(newVal)
    requestAnimationFrame(() => {
      ta.focus()
      ta.setSelectionRange(start + marker.length, start + marker.length + selected.length)
    })
  }, [callOnChange, textareaRef])

  const applyList = useCallback((type: 'bullet' | 'numbered' | 'lettered') => {
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end   = ta.selectionEnd

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
    const newVal = ta.value.slice(0, lineStart) + newSelected + ta.value.slice(lineEnd)
    callOnChange(newVal)
    requestAnimationFrame(() => {
      ta.focus()
      ta.setSelectionRange(lineStart, lineStart + newSelected.length)
    })
  }, [callOnChange, textareaRef])

  const handleBold      = useCallback(() => wrapSelection('**'), [wrapSelection])
  const handleItalic    = useCallback(() => wrapSelection('*'),  [wrapSelection])
  const handleUnderline = useCallback(() => wrapSelection('__'), [wrapSelection])
  const handleBullet    = useCallback(() => applyList('bullet'),   [applyList])
  const handleNumbered  = useCallback(() => applyList('numbered'), [applyList])
  const handleLettered  = useCallback(() => applyList('lettered'), [applyList])

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const meta = e.metaKey || e.ctrlKey
    if (!meta) return
    if (e.key === 'z' && !e.shiftKey) { e.preventDefault(); undo(); return }
    if ((e.key === 'z' && e.shiftKey) || e.key === 'y') { e.preventDefault(); redo(); return }
    if (e.key === 'b') { e.preventDefault(); handleBold() }
    if (e.key === 'i') { e.preventDefault(); handleItalic() }
    if (e.key === 'u') { e.preventDefault(); handleUnderline() }
  }, [handleBold, handleItalic, handleUnderline, undo, redo])

  const handlePaste = useCallback((e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const html = e.clipboardData.getData('text/html')
    if (!html) return
    const converted = htmlToRichText(html)
    if (!converted) return
    e.preventDefault()
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end = ta.selectionEnd
    const newValue = ta.value.slice(0, start) + converted + ta.value.slice(end)
    callOnChange(newValue)
    requestAnimationFrame(() => {
      ta.focus()
      ta.setSelectionRange(start + converted.length, start + converted.length)
    })
  }, [callOnChange, textareaRef])

  return (
    <div className={className}>
      <div className="flex items-center gap-0.5 border border-dark-600 border-b-0 rounded-t bg-dark-800 px-2 py-1">
        <ToolbarButton onAction={handleBold}      title="Pogrubienie (⌘B)"><Bold className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleItalic}    title="Kursywa (⌘I)"><Italic className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleUnderline} title="Podkreślenie (⌘U)"><Underline className="h-3.5 w-3.5" /></ToolbarButton>
        <span className="w-px h-4 bg-dark-600 mx-1.5" />
        <ToolbarButton onAction={handleBullet}   title="Lista punktowana (•)"><List className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleNumbered} title="Lista numerowana (1.)"><ListOrdered className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleLettered} title="Lista literowa (a))">
          <span className="text-xs font-mono leading-none px-0.5">a)</span>
        </ToolbarButton>
        <span className="ml-auto text-xs text-dark-500 hidden sm:block">Zaznacz tekst → kliknij styl</span>
      </div>
      <textarea
        ref={internalRef}
        value={value}
        onChange={(e) => callOnChange(e.target.value)}
        onKeyDown={handleKeyDown}
        onPaste={handlePaste}
        rows={rows}
        placeholder={placeholder}
        className="w-full bg-dark-700 border border-dark-600 rounded-b px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 resize-y min-h-0 text-sm"
      />
    </div>
  )
})
