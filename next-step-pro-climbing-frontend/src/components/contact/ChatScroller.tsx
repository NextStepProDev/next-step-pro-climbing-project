import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { ArrowDown, Loader2 } from 'lucide-react'
import type { ChatMessage } from '../../utils/chatMessages'

/** Closer than this to the bottom counts as "at the bottom" — a pixel or two of rounding must not. */
const BOTTOM_SLACK_PX = 80
/** Scrolling up within this of the top asks for the older page before the reader hits the wall. */
const TOP_TRIGGER_PX = 80

interface ChatScrollerProps {
  /** The window's messages, oldest first — only their ids and order are read here. */
  messages: readonly ChatMessage[]
  hasOlder: boolean
  loadingOlder: boolean
  onLoadOlder: () => void
  /** Whether a message is the reader's own: sending one always scrolls down to it. */
  isOwn: (message: ChatMessage) => boolean
  loadOlderLabel: string
  newBelowLabel: string
  className?: string
  children: ReactNode
}

/**
 * The scroll box of a chat window, and the three rules that keep it from jumping under the reader:
 *
 * 1. **New messages scroll down only for a reader already at the bottom** (or for their own
 *    message). Somebody reading older messages stays where they are; a "new messages" pill
 *    offers the way down instead.
 * 2. **An older page loaded on top keeps the reader's place**: the scroll position moves by
 *    exactly the height that appeared above, so the message being read does not move.
 * 3. A poll only ever APPENDS (the list is keyed by message id), so nothing above the reader is
 *    re-created under them.
 *
 * All state changes happen in event handlers; the layout effect only moves the scroll position,
 * before the browser paints, so the reader never sees the intermediate frame.
 */
export function ChatScroller({
  messages, hasOlder, loadingOlder, onLoadOlder, isOwn, loadOlderLabel, newBelowLabel, className, children,
}: ChatScrollerProps) {
  const boxRef = useRef<HTMLDivElement>(null)
  const atBottomRef = useRef(true)
  // Taken just before an older page is asked for; applied when it arrives (the first id changes).
  const prependSnapshotRef = useRef<{ height: number; top: number } | null>(null)
  const shownRef = useRef<{ firstId: string | null; lastId: string | null; count: number }>(
    { firstId: null, lastId: null, count: 0 },
  )
  // The newest message the reader had when they scrolled away from the bottom; null at the bottom.
  const [leftAtId, setLeftAtId] = useState<string | null>(null)

  const firstId = messages.length > 0 ? messages[0].id : null
  const last = messages.length > 0 ? messages[messages.length - 1] : null
  const lastId = last?.id ?? null

  useLayoutEffect(() => {
    const box = boxRef.current
    if (!box) return
    const shown = shownRef.current
    const snapshot = prependSnapshotRef.current

    if (shown.count === 0 && messages.length > 0) {
      box.scrollTop = box.scrollHeight
    } else if (snapshot && firstId !== shown.firstId) {
      box.scrollTop = snapshot.top + (box.scrollHeight - snapshot.height)
      prependSnapshotRef.current = null
    } else if (last && lastId !== shown.lastId && (atBottomRef.current || isOwn(last))) {
      box.scrollTop = box.scrollHeight
      atBottomRef.current = true
    }
    shownRef.current = { firstId, lastId, count: messages.length }
  }, [messages, firstId, last, lastId, isOwn])

  const requestOlder = () => {
    const box = boxRef.current
    if (!box || loadingOlder || !hasOlder) return
    prependSnapshotRef.current = { height: box.scrollHeight, top: box.scrollTop }
    onLoadOlder()
  }

  const onScroll = () => {
    const box = boxRef.current
    if (!box) return
    const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight <= BOTTOM_SLACK_PX
    atBottomRef.current = atBottom
    if (atBottom) {
      if (leftAtId !== null) setLeftAtId(null)
    } else if (leftAtId === null) {
      setLeftAtId(lastId)
    }
    if (box.scrollTop <= TOP_TRIGGER_PX) requestOlder()
  }

  const jumpToBottom = () => {
    const box = boxRef.current
    if (!box) return
    box.scrollTop = box.scrollHeight
    atBottomRef.current = true
    setLeftAtId(null)
  }

  const leftIndex = leftAtId === null ? -1 : messages.findIndex((m) => m.id === leftAtId)
  const newBelow = leftAtId !== null && leftIndex >= 0 ? messages.length - 1 - leftIndex : 0

  return (
    <div className="relative">
      <div ref={boxRef} onScroll={onScroll} className={className}>
        {hasOlder && (
          <div className="flex justify-center pb-2">
            <button
              type="button"
              onClick={requestOlder}
              disabled={loadingOlder}
              className="inline-flex items-center gap-1.5 text-xs text-surface-400 hover:text-surface-200 disabled:opacity-60"
            >
              {loadingOlder && <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />}
              {loadOlderLabel}
            </button>
          </div>
        )}
        {children}
      </div>
      {newBelow > 0 && (
        <button
          type="button"
          onClick={jumpToBottom}
          className="absolute bottom-3 left-1/2 -translate-x-1/2 inline-flex items-center gap-1.5 rounded-full bg-primary-600 px-3 py-1.5 text-xs font-medium text-white shadow-lg shadow-black/30 hover:bg-primary-500"
        >
          <ArrowDown className="w-3.5 h-3.5" aria-hidden="true" />
          {newBelowLabel}
        </button>
      )}
    </div>
  )
}
