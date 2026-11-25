import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useQuery, type QueryKey } from '@tanstack/react-query'
import { BEFORE_EVERYTHING, mergeChatMessages, type ChatMessage } from '../../utils/chatMessages'

/** How often an open chat asks for new messages. Cheap: an idle poll returns an empty list. */
export const CHAT_POLL_MS = 5_000

interface Page<M> {
  messages: M[]
  hasOlder: boolean
}

interface Options<M extends ChatMessage, L extends Page<M>, U extends { messages: M[] }> {
  /** The newest page. Read once per open window; polls and scrolling extend it from there. */
  latestKey: QueryKey
  fetchLatest: () => Promise<L>
  fetchOlder: (at: string, id: string) => Promise<Page<M>>
  fetchNewer: (at: string, id: string) => Promise<U>
  pollMs?: number
}

/**
 * One chat conversation as a window over its messages: opens on the newest page, loads older
 * pages on demand, and polls ONLY for what arrived after the newest message it has. Shared by the
 * team's panel and the person's chat so the paging rules live in one place.
 *
 * The pieces (newest page, older pages, polled messages, a message just sent) are merged by id and
 * sorted in the server's order every render, so an overlap - a poll racing a refetch, a sent
 * message coming back on the next poll - shows up once, in the right place.
 */
/** A chat message as the window needs it: its order, and the files a reader may remove. */
type WindowMessage = ChatMessage & { body: string | null; files: { id: string }[] }

export function useChatWindow<M extends WindowMessage, L extends Page<M>, U extends { messages: M[] }>(
  { latestKey, fetchLatest, fetchOlder, fetchNewer, pollMs = CHAT_POLL_MS }: Options<M, L, U>,
) {
  const latest = useQuery({
    queryKey: latestKey,
    queryFn: fetchLatest,
    // By id: another conversation's page must never stand in while this one loads.
    placeholderData: undefined,
    // The window grows by itself (polls, scrolling); re-reading the newest page would only repeat it.
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  const [older, setOlder] = useState<M[]>([])
  const [olderExhausted, setOlderExhausted] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [olderError, setOlderError] = useState<unknown>(null)
  const [arrived, setArrived] = useState<M[]>([])
  const [hiddenFiles, setHiddenFiles] = useState<ReadonlySet<string>>(() => new Set())

  // ⚠️ Messages are otherwise immutable (polls only ask for what is new), so a file the reader
  // removed is taken out here; a message that was only that file goes with it, as on the server.
  // The OTHER side sees the removal when it reopens the conversation - until then the thumbnail
  // answers 404 and shows its "could not load" state.
  const messages = useMemo(() => {
    const merged = mergeChatMessages(older, latest.data?.messages ?? [], arrived)
    if (hiddenFiles.size === 0) return merged
    return merged
      .map((m) => (m.files.some((f) => hiddenFiles.has(f.id))
        ? { ...m, files: m.files.filter((f) => !hiddenFiles.has(f.id)) }
        : m))
      .filter((m) => m.body !== null || m.files.length > 0)
  }, [older, latest.data, arrived, hiddenFiles])

  // Read by the poll's query function, which must not change identity with every new message.
  const newestRef = useRef<ChatMessage>(BEFORE_EVERYTHING)
  useLayoutEffect(() => {
    newestRef.current = messages.length > 0 ? messages[messages.length - 1] : BEFORE_EVERYTHING
  }, [messages])

  const poll = useQuery({
    queryKey: [...latestKey, 'newer'],
    queryFn: async () => {
      const update = await fetchNewer(newestRef.current.createdAt, newestRef.current.id)
      // Folded in here, where the answer lands, rather than in an effect watching the data: the
      // answer is a delta, and an identical empty one must not be mistaken for "nothing changed".
      if (update.messages.length > 0) {
        setArrived((current) => mergeChatMessages(current, update.messages))
      }
      return update
    },
    enabled: latest.isSuccess,
    refetchInterval: pollMs,
    refetchIntervalInBackground: false,
    // Every answer is a delta against the cursor at the time; a cached one would replay old news.
    gcTime: 0,
  })
  const polled = poll.data

  const hasOlder = !olderExhausted && (older.length > 0 ? true : latest.data?.hasOlder ?? false)

  // A ref, not the state: scrolling near the top fires many scroll events before the state that
  // says "already loading" reaches the next render, and each would ask for the same page again.
  const loadingOlderRef = useRef(false)
  const loadOlder = useCallback(async () => {
    const oldest = messages[0]
    if (!oldest || loadingOlderRef.current || !hasOlder) return
    loadingOlderRef.current = true
    setLoadingOlder(true)
    setOlderError(null)
    try {
      const page = await fetchOlder(oldest.createdAt, oldest.id)
      setOlder((current) => mergeChatMessages(page.messages, current))
      if (!page.hasOlder) setOlderExhausted(true)
    } catch (e) {
      setOlderError(e)
    } finally {
      loadingOlderRef.current = false
      setLoadingOlder(false)
    }
  }, [messages, hasOlder, fetchOlder])

  /** A message the reader has just sent, shown at once instead of on the next poll. */
  const addMessage = useCallback((message: M) => {
    setArrived((current) => mergeChatMessages(current, [message]))
  }, [])

  /** A file this reader has just removed: gone from the window at once. */
  const hideFile = useCallback((fileId: string) => {
    setHiddenFiles((current) => new Set(current).add(fileId))
  }, [])

  const { refetch: refetchPoll } = poll
  /** Ask for news now, e.g. right after sending through an endpoint that does not echo the message. */
  const pollNow = useCallback(() => { void refetchPoll() }, [refetchPoll])

  return {
    latest: latest.data,
    /** The freshest poll answer, for what it carries besides messages (unread flags). */
    lastPoll: polled,
    messages,
    hasOlder,
    loadOlder,
    loadingOlder,
    olderError,
    addMessage,
    hideFile,
    pollNow,
    isLoading: latest.isLoading,
    isError: latest.isError,
    error: latest.error,
    refetch: latest.refetch,
  }
}
