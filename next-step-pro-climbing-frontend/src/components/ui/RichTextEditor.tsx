import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Bold, Italic, Underline, Strikethrough, Heading, List, ListOrdered } from 'lucide-react'
import { BULLET_MARKER, LIST_PREFIX_RE, continueList, normalizeBulletMarker } from '../../utils/richTextInput'

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
        .map(li => `${BULLET_MARKER}${convertChildren(li).trim()}`)
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
      case 's': case 'strike': case 'del': return `~~${inner.trim()}~~`
      case 'br':               return '\n'
      // Word and Google Docs paste their section titles as real headings; keeping the marker
      // means a pasted plan arrives already sectioned instead of flattened into one wall.
      case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6':
        return inner.trim() ? `## ${inner.trim()}\n` : ''
      case 'p':
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
  maxLength?: number
  autoFocus?: boolean
  /** Textarea classes, for call sites whose field is styled differently from the CMS panels. */
  inputClassName?: string
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
      className="p-1 rounded text-surface-300 hover:text-surface-100 hover:bg-surface-700 transition-colors"
      title={title}
      aria-label={title}
    >
      {children}
    </button>
  )
}

const HEADING_PREFIX_RE = /^#{1,3} /
const HEADING_MARKER = '## '

/** A line is a heading or a list item, never both — so switching one off strips whichever is there. */
function stripBlockPrefix(line: string): string {
  return line.replace(LIST_PREFIX_RE, '').replace(HEADING_PREFIX_RE, '')
}

export const RichTextEditor = forwardRef<HTMLTextAreaElement, RichTextEditorProps>(function RichTextEditor({
  value,
  onChange,
  rows = 16,
  placeholder,
  className,
  maxLength,
  autoFocus,
  inputClassName,
}, forwardedRef) {
  const { t } = useTranslation('common')
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

  /** Applies a computed edit and puts the caret where the rule said it belongs. */
  const applyEdit = useCallback((newValue: string, caret: number) => {
    const ta = textareaRef.current
    callOnChange(newValue)
    requestAnimationFrame(() => {
      if (!ta) return
      ta.focus()
      ta.setSelectionRange(caret, caret)
    })
  }, [callOnChange, textareaRef])

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

  const applyBlock = useCallback((type: 'bullet' | 'numbered' | 'lettered' | 'heading') => {
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end   = ta.selectionEnd

    const lineStart  = ta.value.lastIndexOf('\n', start - 1) + 1
    const lineEndIdx = ta.value.indexOf('\n', end)
    const lineEnd    = lineEndIdx === -1 ? ta.value.length : lineEndIdx

    const lines = ta.value.slice(lineStart, lineEnd).split('\n')

    const allBullet   = lines.every(l => /^[•\-*] /.test(l))
    const allNumbered = lines.every(l => /^\d+\. /.test(l))
    const allLettered = lines.every(l => /^[a-z]\) /i.test(l))
    const allHeading  = lines.every(l => HEADING_PREFIX_RE.test(l))

    let newLines: string[]
    if (type === 'bullet') {
      newLines = allBullet
        ? lines.map(stripBlockPrefix)
        : lines.map(l => `${BULLET_MARKER}${stripBlockPrefix(l)}`)
    } else if (type === 'numbered') {
      newLines = allNumbered
        ? lines.map(stripBlockPrefix)
        : lines.map((l, i) => `${i + 1}. ${stripBlockPrefix(l)}`)
    } else if (type === 'lettered') {
      newLines = allLettered
        ? lines.map(stripBlockPrefix)
        // Clamped at 'z': past 26 lines the next code points are '{', '|', '}'
        : lines.map((l, i) => `${String.fromCharCode(97 + Math.min(i, 25))}) ${stripBlockPrefix(l)}`)
    } else {
      newLines = allHeading
        ? lines.map(stripBlockPrefix)
        : lines.map(l => `${HEADING_MARKER}${stripBlockPrefix(l)}`)
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
  const handleStrike    = useCallback(() => wrapSelection('~~'), [wrapSelection])
  const handleHeading   = useCallback(() => applyBlock('heading'),  [applyBlock])
  const handleBullet    = useCallback(() => applyBlock('bullet'),   [applyBlock])
  const handleNumbered  = useCallback(() => applyBlock('numbered'), [applyBlock])
  const handleLettered  = useCallback(() => applyBlock('lettered'), [applyBlock])

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const meta = e.metaKey || e.ctrlKey

    // Enter inside a list carries the list on — writing ten points otherwise means ten trips
    // to the toolbar. Here Enter is the key that makes a new line; in the comment thread it
    // sends the message, which is why the rule itself lives outside this component.
    if (!meta && e.key === 'Enter') {
      const ta = e.currentTarget
      const edit = continueList(ta.value, ta.selectionStart, ta.selectionEnd, maxLength)
      if (edit) {
        e.preventDefault()
        applyEdit(edit.value, edit.caret)
      }
      return
    }

    if (!meta) return
    if (e.key === 'z' && !e.shiftKey) { e.preventDefault(); undo(); return }
    if ((e.key === 'z' && e.shiftKey) || e.key === 'y') { e.preventDefault(); redo(); return }
    if (e.key === 'b') { e.preventDefault(); handleBold() }
    if (e.key === 'i') { e.preventDefault(); handleItalic() }
    if (e.key === 'u') { e.preventDefault(); handleUnderline() }
    if (e.shiftKey && (e.key === 'x' || e.key === 'X')) { e.preventDefault(); handleStrike() }
  }, [applyEdit, handleBold, handleItalic, handleUnderline, handleStrike, maxLength, undo, redo])

  const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const edit = normalizeBulletMarker(e.target.value, e.target.selectionStart)
    if (edit) {
      applyEdit(edit.value, edit.caret)
      return
    }
    callOnChange(e.target.value)
  }, [applyEdit, callOnChange])

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
      <div className="flex items-center gap-0.5 border border-surface-600 border-b-0 rounded-t bg-surface-800 px-2 py-1">
        <ToolbarButton onAction={handleBold}      title={t('richText.bold')}><Bold className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleItalic}    title={t('richText.italic')}><Italic className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleUnderline} title={t('richText.underline')}><Underline className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleStrike}    title={t('richText.strikethrough')}><Strikethrough className="h-3.5 w-3.5" /></ToolbarButton>
        <span className="w-px h-4 bg-surface-600 mx-1.5" />
        <ToolbarButton onAction={handleHeading}  title={t('richText.heading')}><Heading className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleBullet}   title={t('richText.bulletList')}><List className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleNumbered} title={t('richText.numberedList')}><ListOrdered className="h-3.5 w-3.5" /></ToolbarButton>
        <ToolbarButton onAction={handleLettered} title={t('richText.letteredList')}>
          <span className="text-xs font-mono leading-none px-0.5">a)</span>
        </ToolbarButton>
        <span className="ml-auto text-xs text-surface-500 hidden sm:block">{t('richText.hint')}</span>
      </div>
      <textarea
        ref={internalRef}
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onPaste={handlePaste}
        rows={rows}
        maxLength={maxLength}
        autoFocus={autoFocus}
        placeholder={placeholder}
        className={inputClassName ?? 'w-full bg-surface-700 border border-surface-600 rounded-b px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500 resize-y min-h-0 text-sm'}
      />
    </div>
  )
})
