import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import clsx from 'clsx'
import { ChevronLeft, ChevronRight, ExternalLink, Mail, MailWarning, MessageSquare, Paperclip, Plus, Search, Trash2, User, X } from 'lucide-react'
import { adminApi, adminContactApi } from '../../api/client'
import { matchesPersonQuery } from '../../utils/personSearch'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { getErrorMessage } from '../../utils/errors'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { ContextChip } from '../../components/contact/ContextChip'
import { CONTACT_BODY_MAX } from '../../components/contact/ContactForm'
import type { AdminConversationMessage, AdminConversationSummary, ContactReplyResult } from '../../types'
import type { ChatMessage } from '../../utils/chatMessages'
import { ChatScroller } from '../../components/contact/ChatScroller'
import { useChatWindow } from '../../components/contact/useChatWindow'
import { useStagedChatFiles } from '../../components/contact/useStagedChatFiles'
import { ChatAttachPicker, ChatMessageFiles } from '../../components/contact/ChatAttachments'
import { RichTextEditor } from '../../components/ui/RichTextEditor'
import { renderRichText, toPlainText } from '../../utils/renderRichText'

type Filter = 'unread' | 'all'

const PAGE_SIZE = 20

/** In the team's window every staff message counts as "own": sending one scrolls down to it. */
const isFromStaff = (m: ChatMessage) => (m as AdminConversationMessage).fromStaff

/** Typing in the search waits this long before asking the server — one request per pause, not per key. */
const SEARCH_DEBOUNCE_MS = 300

/**
 * The message centre: one conversation per person (an account or a guest address), ordered like a
 * messenger — the newest message in EITHER direction on top. Opening a conversation marks it read
 * for the whole team; a reply stays here and goes out by mail only to a guest (an account holder's
 * conversation is an in-app chat).
 *
 * URL state: `?conversation=` is the open conversation (also the deep link from a guest's
 * notification mail); `?new=1` opens "New conversation", `?new=<userId>` opens it for that person
 * (the "Write" button on a user's card).
 */
export function AdminMessagesPanel() {
  const { t } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('conversation')
  const newParam = searchParams.get('new')
  const composing = newParam !== null && !selectedId
  const [filter, setFilter] = useState<Filter>('all')
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [query, setQuery] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(searchInput.trim())
      setPage(0)
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput])

  // `new` and `conversation` are two answers to "what is on the right" — setting one clears the other
  const select = useCallback((id: string | null) => {
    setSearchParams((current) => {
      const params = new URLSearchParams(current)
      params.delete('new')
      if (id) params.set('conversation', id)
      else params.delete('conversation')
      return params
    }, { replace: !id })
  }, [setSearchParams])

  const startNew = () => {
    const params = new URLSearchParams(searchParams)
    params.delete('conversation')
    params.set('new', '1')
    setSearchParams(params)
  }

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'contact', 'list', filter, page, query],
    queryFn: () => adminContactApi.list(filter === 'unread', page, PAGE_SIZE, query),
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  })

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-6">
      {/* The list stays on screen next to the conversation from lg up; below that one replaces the other */}
      <section className={clsx('space-y-4', (selectedId || composing) && 'max-lg:hidden')} aria-label={t('contactMessages.listLabel')}>
        <div className="flex items-center gap-2">
          <div className="relative flex-1 min-w-0">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-surface-500 pointer-events-none" aria-hidden="true" />
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder={t('contactMessages.searchPlaceholder')}
              aria-label={t('contactMessages.searchLabel')}
              className="w-full pl-9 pr-3 py-2 bg-surface-900 border border-surface-700 rounded-lg text-sm text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>
          <Button type="button" size="sm" onClick={startNew} className="shrink-0">
            <span className="inline-flex items-center gap-1.5">
              <Plus className="w-4 h-4" aria-hidden="true" />
              {t('contactMessages.newConversation')}
            </span>
          </Button>
        </div>

        <div className="flex gap-2">
          {(['all', 'unread'] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => { setFilter(f); setPage(0) }}
              className={clsx(
                'px-3 py-1.5 text-sm rounded-lg border transition-colors',
                filter === f
                  ? 'bg-primary-600 border-primary-500 text-white'
                  : 'bg-surface-900 border-surface-700 text-surface-400 hover:text-surface-200',
              )}
            >
              {t(`contactMessages.filter.${f}`)}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="py-12 flex justify-center"><LoadingSpinner /></div>
        ) : isError ? (
          <QueryError error={error} onRetry={() => refetch()} />
        ) : !data || data.content.length === 0 ? (
          <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 text-center text-sm text-surface-400">
            {query
              ? t('contactMessages.emptySearch', { query })
              : filter === 'unread' ? t('contactMessages.emptyUnread') : t('contactMessages.empty')}
          </div>
        ) : (
          <>
            <ul className="space-y-2">
              {data.content.map((c) => (
                <li key={c.id}>
                  <ConversationRow summary={c} active={c.id === selectedId} onSelect={() => select(c.id)} />
                </li>
              ))}
            </ul>
            {data.totalPages > 1 && (
              <div className="flex items-center justify-between text-sm text-surface-400">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="inline-flex items-center gap-1 disabled:opacity-40"
                >
                  <ChevronLeft className="w-4 h-4" aria-hidden="true" />
                  {t('contactMessages.prev')}
                </button>
                <span>{t('contactMessages.page', { page: page + 1, total: data.totalPages })}</span>
                <button
                  type="button"
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="inline-flex items-center gap-1 disabled:opacity-40"
                >
                  {t('contactMessages.next')}
                  <ChevronRight className="w-4 h-4" aria-hidden="true" />
                </button>
              </div>
            )}
          </>
        )}
      </section>

      <section className={clsx(!selectedId && !composing && 'max-lg:hidden')}>
        {selectedId ? (
          // Keyed by id: a half-written reply must never carry over to another person's conversation
          <ConversationDetail key={selectedId} conversationId={selectedId} onBack={() => select(null)} />
        ) : composing ? (
          // Keyed by the person: "Write" on another card must start clean, not inherit this draft
          <NewConversation
            key={newParam}
            preselectedUserId={newParam === '1' ? null : newParam}
            onOpen={select}
            onCancel={() => select(null)}
          />
        ) : (
          <div className="h-full min-h-48 bg-surface-900 border border-dashed border-surface-700 rounded-xl flex flex-col items-center justify-center gap-2 text-sm text-surface-500 p-6">
            <MessageSquare className="w-8 h-8 text-surface-600" aria-hidden="true" />
            {t('contactMessages.pick')}
          </div>
        )}
      </section>
    </div>
  )
}

function ConversationRow({ summary, active, onSelect }: {
  summary: AdminConversationSummary
  active: boolean
  onSelect: () => void
}) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={active ? 'true' : undefined}
      className={clsx(
        // relative: anchors the sr-only text to the row, not the document (CLAUDE.md, sr-only trap)
        'relative w-full text-left rounded-xl border p-4 transition-colors',
        active ? 'bg-surface-800 border-primary-500/60' : 'bg-surface-900 border-surface-800 hover:border-surface-600',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex items-center gap-2 text-sm font-semibold text-surface-100">
            {summary.unread && (
              <>
                {/* The dot is for eyes; the words are for a screen reader, which ignores an
                    aria-label on a role-less span */}
                <span className="w-2 h-2 rounded-full bg-rose-500 shrink-0" aria-hidden="true" />
                <span className="sr-only">{t('contactMessages.unread')}</span>
              </>
            )}
            <span className="truncate">{summary.name}</span>
            {!summary.userId && (
              <span className="text-[10px] uppercase tracking-wide text-surface-500 border border-surface-700 rounded px-1">
                {t('contactMessages.guest')}
              </span>
            )}
          </p>
          <p className="text-xs text-surface-500 truncate">{summary.email}</p>
        </div>
        <span className="text-[11px] text-surface-500 shrink-0">
          {/* The ordering clock: the newest message either way. The inbound clock is null in a
              conversation the team started, and `new Date(null)` is 1 January 1970 — the row
              would quietly show "1 Jan, 01:00" instead of when anything was written. */}
          {format(new Date(summary.lastActivityAt), 'd MMM, HH:mm', { locale })}
        </span>
      </div>
      {(summary.preview || summary.previewIsFile) && (
        <p className="mt-2 text-sm text-surface-400 line-clamp-2 break-words">
          {summary.previewFromStaff && <span className="text-surface-500">{t('contactMessages.you')} </span>}
          {summary.previewIsFile ? (
            <span className="inline-flex items-center gap-1 italic">
              <Paperclip className="w-3.5 h-3.5" aria-hidden="true" />
              {t('contactMessages.previewFile')}
            </span>
          ) : toPlainText(summary.preview ?? '')}
        </p>
      )}
      <div className="mt-2 flex items-center justify-between gap-2">
        {/* min-w-0: a flex item will not shrink below its content otherwise, so a long course
            title pushed "awaiting reply" off a phone screen */}
        <div className="min-w-0">
          {summary.previewContextType && !summary.previewFromStaff && (
            <ContextChip
              stored={{ contextType: summary.previewContextType, courseTitle: summary.previewCourseTitle, date: summary.previewDate }}
              compact
            />
          )}
        </div>
        {!summary.answered && (
          <span className="text-[11px] text-amber-500 shrink-0">{t('contactMessages.awaiting')}</span>
        )}
      </div>
    </button>
  )
}

function ConversationDetail({ conversationId, onBack }: { conversationId: string; onBack: () => void }) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')
  const [lastResult, setLastResult] = useState<ContactReplyResult | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  // A chat window: the newest page, older pages on scroll, and a 5 s poll for only what is new.
  const fetchLatest = useCallback(() => adminContactApi.get(conversationId), [conversationId])
  const fetchOlder = useCallback(
    (at: string, id: string) => adminContactApi.older(conversationId, at, id), [conversationId])
  const fetchNewer = useCallback(
    (at: string, id: string) => adminContactApi.newer(conversationId, at, id), [conversationId])
  const chat = useChatWindow({
    latestKey: ['admin', 'contact', 'conversation', conversationId],
    fetchLatest,
    fetchOlder,
    fetchNewer,
  })
  const summary = chat.lastPoll?.summary ?? chat.latest?.summary

  // The list and the badges, never this conversation's own window: the window keeps itself
  // current by polling, and re-reading its newest page would only repeat what it already shows.
  const invalidateAround = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'contact', 'list'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
  }

  // Showing an unread conversation IS reading it — the badge is about what nobody has looked at.
  // ⚠️ Keyed on the person's newest message, not on the flag alone: a poll answered before the
  // last mark-read still says "unread", and a new message after it says "unread" again — the flag
  // never changes in between, so an effect on it would leave the second message lit for good.
  const unreadMarker = summary?.unread ? (summary.lastInboundAt ?? 'unread') : null
  const { mutate: markRead } = useMutation({
    mutationFn: () => adminContactApi.markRead(conversationId),
    onSuccess: invalidateAround,
  })
  useEffect(() => {
    if (unreadMarker) markRead()
  }, [unreadMarker, markRead])

  const { addMessage } = chat
  const attachments = useStagedChatFiles()
  const { hideFile } = chat
  // Text alone by the plain endpoint (outside the upload rate limit); with files, multipart.
  const replyMutation = useMutation({
    mutationFn: () => {
      const body = draft.trim()
      return attachments.staged.length > 0
        ? adminContactApi.replyWithFiles(conversationId, body || null, attachments.staged)
        : adminContactApi.reply(conversationId, body)
    },
    onSuccess: (result) => {
      setDraft('')
      attachments.clear()
      setLastResult(result)
      addMessage(result.message)
      invalidateAround()
    },
  })

  const deleteFileMutation = useMutation({
    mutationFn: (fileId: string) => adminContactApi.deleteFile(fileId),
    onSuccess: (_, fileId) => {
      hideFile(fileId)
      invalidateAround()
    },
  })

  const canReply = (draft.trim().length > 0 || attachments.staged.length > 0) && !attachments.picking

  const deleteMutation = useMutation({
    mutationFn: () => adminContactApi.delete(conversationId),
    onSuccess: () => {
      setConfirmDelete(false)
      // Only the list and the badge — never this conversation's own query: it is still mounted
      // until the URL change re-renders, and invalidating it would refetch a conversation that no
      // longer exists (400). It goes inactive on unmount and is garbage-collected from there.
      onBack()
      queryClient.invalidateQueries({ queryKey: ['admin', 'contact', 'list'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
    },
  })

  if (chat.isLoading) return <div className="py-12 flex justify-center"><LoadingSpinner /></div>
  if (chat.isError || !summary) return <QueryError error={chat.error} onRetry={() => chat.refetch()} />

  const messages = chat.messages

  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl">
      <div className="p-4 border-b border-surface-800 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <button
            type="button"
            onClick={onBack}
            className="lg:hidden inline-flex items-center gap-1 text-xs text-surface-400 hover:text-surface-200 mb-2"
          >
            <ChevronLeft className="w-4 h-4" aria-hidden="true" />
            {t('contactMessages.back')}
          </button>
          <h2 className="text-base font-semibold text-surface-100 truncate">{summary.name}</h2>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1 text-xs text-surface-400">
            <a href={`mailto:${summary.email}`} className="inline-flex items-center gap-1 hover:text-primary-400 min-w-0">
              <Mail className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
              <span className="truncate">{summary.email}</span>
            </a>
            {summary.userId ? (
              <Link to={`/admin/users/${summary.userId}`} className="inline-flex items-center gap-1 hover:text-primary-400">
                <User className="w-3.5 h-3.5" aria-hidden="true" />
                {t('contactMessages.userCard')}
                <ExternalLink className="w-3 h-3" aria-hidden="true" />
              </Link>
            ) : (
              <span className="text-surface-500">{t('contactMessages.guestHint')}</span>
            )}
          </div>
        </div>
        <button
          type="button"
          onClick={() => setConfirmDelete(true)}
          // A second delete while the first is in flight would hit a conversation that is gone
          // and report a failure that never happened
          disabled={deleteMutation.isPending}
          className="p-2 rounded-lg text-surface-500 hover:text-rose-400 hover:bg-rose-500/10 shrink-0 disabled:opacity-40"
          aria-label={t('contactMessages.delete')}
          title={t('contactMessages.delete')}
        >
          <Trash2 className="w-4 h-4" aria-hidden="true" />
        </button>
      </div>

      {/* ConfirmModal closes on confirm, so a failed delete has to be said here, on the conversation */}
      {deleteMutation.isError && (
        <p role="alert" className="mx-4 mt-4 text-sm text-rose-400">{getErrorMessage(deleteMutation.error)}</p>
      )}

      {chat.olderError != null && (
        <p role="alert" className="mx-4 mt-4 text-sm text-rose-400">{getErrorMessage(chat.olderError)}</p>
      )}

      <ChatScroller
        messages={messages}
        hasOlder={chat.hasOlder}
        loadingOlder={chat.loadingOlder}
        onLoadOlder={chat.loadOlder}
        isOwn={isFromStaff}
        loadOlderLabel={t('contactMessages.loadOlder')}
        newBelowLabel={t('contactMessages.newBelow')}
        className="p-4 max-h-[60vh] overflow-y-auto"
      >
      <ol className="space-y-3" aria-label={t('contactMessages.messagesLabel')}>
        {messages.map((m) => (
          <li key={m.id} className={clsx('flex', m.fromStaff ? 'justify-end' : 'justify-start')}>
            <div
              className={clsx(
                'max-w-[85%] rounded-2xl px-4 py-3 border',
                m.fromStaff
                  ? 'bg-primary-600/15 border-primary-500/30 rounded-br-sm'
                  : 'bg-surface-800 border-surface-700 rounded-bl-sm',
              )}
            >
              <p className="text-[11px] text-surface-500 mb-1">
                {m.fromStaff ? (m.staffAuthorName ?? t('contactMessages.team')) : summary.name}
                {' · '}
                {format(new Date(m.createdAt), 'd MMM yyyy, HH:mm', { locale })}
                {!m.fromStaff && <> {' · '}{m.language.toUpperCase()}</>}
              </p>
              {!m.fromStaff && (
                <div className="mb-1">
                  <ContextChip stored={m} compact />
                </div>
              )}
              {/* A div, never a p: the renderer returns lists. Stored unescaped; the renderer escapes. */}
              {m.body !== null && (
                <div
                  className="text-sm text-surface-100 whitespace-pre-wrap break-words"
                  dangerouslySetInnerHTML={{ __html: renderRichText(m.body) }}
                />
              )}
              <ChatMessageFiles files={m.files} onDelete={(fileId) => deleteFileMutation.mutate(fileId)} />
            </div>
          </li>
        ))}
      </ol>
      </ChatScroller>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (canReply && !replyMutation.isPending) replyMutation.mutate()
        }}
        className="p-4 border-t border-surface-800 space-y-3"
      >
        <label htmlFor="admin-contact-reply" className="block text-sm font-medium text-surface-300">
          {t('contactMessages.reply')}
        </label>
        <RichTextEditor
          id="admin-contact-reply"
          rows={4}
          maxLength={CONTACT_BODY_MAX}
          value={draft}
          onChange={(value) => { setDraft(value); setLastResult(null) }}
        />
        {/* Files only in an account's chat: a guest is reached by mail alone (the server refuses too) */}
        {summary.userId && (
          <ChatAttachPicker
            staged={attachments.staged}
            picking={attachments.picking}
            error={attachments.error}
            onPick={attachments.pick}
            onRemove={attachments.remove}
            disabled={replyMutation.isPending}
          />
        )}
        {replyMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(replyMutation.error)}</p>
        )}
        {deleteFileMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(deleteFileMutation.error)}</p>
        )}
        {lastResult && (
          // Both outcomes are success: an account holder's reply staying in the app is the design,
          // not a missed mail, so it must not wear the warning colour.
          <p role="status" className="text-sm text-green-400">
            {lastResult.mailed ? t('contactMessages.sentMailed') : t('contactMessages.sentInAppOnly')}
          </p>
        )}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-xs text-surface-500">
            {summary.userId ? t('contactMessages.replyHintUser') : t('contactMessages.replyHintGuest')}
          </p>
          <Button type="submit" loading={replyMutation.isPending} disabled={!canReply}>
            {t('contactMessages.send')}
          </Button>
        </div>
      </form>

      <ConfirmModal
        isOpen={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => deleteMutation.mutate()}
        title={t('contactMessages.deleteTitle')}
        message={t('contactMessages.deleteMessage', { name: summary.name })}
        confirmText={t('contactMessages.delete')}
        variant="danger"
      />
    </div>
  )
}

/**
 * The team writes first. Only to a confirmed, non-admin account: an unconfirmed one cannot sign in,
 * an admin has no chat link, and a guest could only be reached by mail. The server refuses all
 * three; this only keeps them from being offered.
 *
 * Still one conversation per person: picking somebody who already has one OPENS it instead.
 */
function NewConversation({ preselectedUserId, onOpen, onCancel }: {
  preselectedUserId: string | null
  onOpen: (conversationId: string) => void
  onCancel: () => void
}) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [pickedId, setPickedId] = useState<string | null>(preselectedUserId)
  const [draft, setDraft] = useState('')
  const [confirmDiscard, setConfirmDiscard] = useState(false)

  const { data: users = [], isLoading: usersLoading } = useQuery({
    // The panel-wide user list — the same key as the invitation picker and the users table
    queryKey: ['admin', 'users'],
    queryFn: adminApi.getAllUsers,
  })
  const picked = pickedId ? users.find((u) => u.id === pickedId) ?? null : null
  const pickedReachable = !!picked && picked.role !== 'ADMIN' && picked.emailVerified

  const lookup = useQuery({
    queryKey: ['admin', 'contact', 'byUser', pickedId],
    queryFn: () => adminContactApi.findForUser(pickedId!),
    enabled: pickedReachable,
    placeholderData: undefined,
  })
  const existingId = lookup.data?.conversationId ?? null
  useEffect(() => {
    if (existingId) onOpen(existingId)
  }, [existingId, onOpen])

  const results = useMemo(() => {
    if (!search.trim()) return []
    return users
      .filter((u) => u.role !== 'ADMIN')
      .filter((u) => matchesPersonQuery(u, search))
      .slice(0, 8)
  }, [search, users])

  const startMutation = useMutation({
    mutationFn: () => adminContactApi.start(pickedId!, draft.trim()),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'contact'] })
      onOpen(result.conversationId)
    },
  })

  const cancel = () => {
    if (draft.trim()) setConfirmDiscard(true)
    else onCancel()
  }

  const canSend = pickedReachable && lookup.isSuccess && !existingId && !!draft.trim() && !startMutation.isPending

  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl">
      <div className="p-4 border-b border-surface-800 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-surface-100">{t('contactMessages.newTitle')}</h2>
        <button
          type="button"
          onClick={cancel}
          className="p-2 rounded-lg text-surface-500 hover:text-surface-200 hover:bg-surface-800"
          aria-label={t('contactMessages.cancel')}
          title={t('contactMessages.cancel')}
        >
          <X className="w-4 h-4" aria-hidden="true" />
        </button>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (canSend) startMutation.mutate()
        }}
        className="p-4 space-y-4"
      >
        <div>
          <label htmlFor="admin-contact-new-person" className="block text-sm font-medium text-surface-300 mb-1">
            {t('contactMessages.pickLabel')}
          </label>
          {pickedId && usersLoading ? (
            <div className="py-3"><LoadingSpinner /></div>
          ) : picked ? (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-surface-700 bg-surface-800 px-3 py-2">
              <div className="min-w-0">
                <p className="text-sm font-medium text-surface-100 truncate">{picked.firstName} {picked.lastName}</p>
                <p className="text-xs text-surface-400 truncate">{picked.email}</p>
              </div>
              <button
                type="button"
                onClick={() => { setPickedId(null); setSearch('') }}
                className="text-xs text-primary-400 hover:text-primary-300 shrink-0"
              >
                {t('contactMessages.change')}
              </button>
            </div>
          ) : (
            <>
              {pickedId && !usersLoading && (
                <p role="alert" className="mb-2 text-sm text-amber-400">{t('contactMessages.personGone')}</p>
              )}
              <input
                id="admin-contact-new-person"
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('contactMessages.pickPlaceholder')}
                autoComplete="off"
                className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-sm text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
              {search.trim() && (
                results.length === 0 ? (
                  <p className="mt-2 text-sm text-surface-500">{t('contactMessages.noPeople')}</p>
                ) : (
                  <ul aria-label={t('contactMessages.pickResults')} className="mt-2 border border-surface-700 rounded-lg divide-y divide-surface-800 overflow-hidden">
                    {results.map((u) => (
                      <li key={u.id}>
                        <button
                          type="button"
                          disabled={!u.emailVerified}
                          title={u.emailVerified ? undefined : t('users.unverifiedHint')}
                          onClick={() => { setPickedId(u.id); setSearch('') }}
                          className="w-full text-left px-3 py-2 hover:bg-surface-800 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <span className="flex items-center gap-2 text-sm text-surface-100">
                            <span className="truncate">{u.firstName} {u.lastName}</span>
                            {!u.emailVerified && (
                              <span className="inline-flex items-center gap-1 text-[11px] text-amber-400 shrink-0">
                                <MailWarning className="w-3 h-3 shrink-0" aria-hidden="true" />
                                {t('users.unverifiedBadge')}
                              </span>
                            )}
                          </span>
                          <span className="block text-xs text-surface-400 truncate">{u.email}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )
              )}
            </>
          )}
          {picked && !pickedReachable && (
            <p role="alert" className="mt-2 text-sm text-amber-400">
              {picked.role === 'ADMIN' ? t('contactMessages.cannotAdmin') : t('users.unverifiedHint')}
            </p>
          )}
          {existingId && (
            <p role="status" className="mt-2 text-sm text-surface-400">{t('contactMessages.openingExisting')}</p>
          )}
          {lookup.isError && (
            <p role="alert" className="mt-2 text-sm text-rose-400">{getErrorMessage(lookup.error)}</p>
          )}
        </div>

        <div>
          <label htmlFor="admin-contact-new-body" className="block text-sm font-medium text-surface-300 mb-1">
            {t('contactMessages.firstMessage')}
          </label>
          <RichTextEditor
            id="admin-contact-new-body"
            rows={5}
            maxLength={CONTACT_BODY_MAX}
            value={draft}
            onChange={setDraft}
          />
        </div>

        {startMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(startMutation.error)}</p>
        )}

        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-xs text-surface-500">{t('contactMessages.pickHint')}</p>
          <Button type="submit" loading={startMutation.isPending} disabled={!canSend}>
            {t('contactMessages.start')}
          </Button>
        </div>
      </form>

      <ConfirmModal
        isOpen={confirmDiscard}
        onClose={() => setConfirmDiscard(false)}
        onConfirm={onCancel}
        title={t('contactMessages.discardTitle')}
        message={t('contactMessages.discardMessage')}
        confirmText={t('contactMessages.discardConfirm')}
        variant="danger"
      />
    </div>
  )
}
