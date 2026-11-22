import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import clsx from 'clsx'
import { ChevronLeft, ChevronRight, ExternalLink, Mail, MessageSquare, Trash2, User } from 'lucide-react'
import { adminContactApi } from '../../api/client'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { getErrorMessage } from '../../utils/errors'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { ContextChip } from '../../components/contact/ContextChip'
import { CONTACT_BODY_MAX } from '../../components/contact/ContactForm'
import type { AdminConversationSummary, ContactReplyResult } from '../../types'

type Filter = 'unread' | 'all'

const PAGE_SIZE = 20

/**
 * "Write to us" conversations: one per person (an account or a guest address), newest message
 * first. Opening a conversation marks it read for the whole team; a reply goes out by mail and
 * stays here. `?conversation=` is the deep link from the notification mail.
 */
export function AdminMessagesPanel() {
  const { t } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('conversation')
  const [filter, setFilter] = useState<Filter>('all')
  const [page, setPage] = useState(0)

  const select = (id: string | null) => {
    const params = new URLSearchParams(searchParams)
    if (id) params.set('conversation', id)
    else params.delete('conversation')
    setSearchParams(params, { replace: !id })
  }

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'contact', 'list', filter, page],
    queryFn: () => adminContactApi.list(filter === 'unread', page, PAGE_SIZE),
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  })

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-6">
      {/* The list stays on screen next to the conversation from lg up; below that one replaces the other */}
      <section className={clsx('space-y-4', selectedId && 'max-lg:hidden')} aria-label={t('contactMessages.listLabel')}>
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
            {filter === 'unread' ? t('contactMessages.emptyUnread') : t('contactMessages.empty')}
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

      <section className={clsx(!selectedId && 'max-lg:hidden')}>
        {selectedId ? (
          // Keyed by id: a half-written reply must never carry over to another person's conversation
          <ConversationDetail key={selectedId} conversationId={selectedId} onBack={() => select(null)} />
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
          {format(new Date(summary.lastReplyAt && summary.previewFromStaff ? summary.lastReplyAt : summary.lastInboundAt),
            'd MMM, HH:mm', { locale })}
        </span>
      </div>
      {summary.preview && (
        <p className="mt-2 text-sm text-surface-400 line-clamp-2 break-words">
          {summary.previewFromStaff && <span className="text-surface-500">{t('contactMessages.you')} </span>}
          {summary.preview}
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

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'contact', 'conversation', conversationId],
    queryFn: () => adminContactApi.get(conversationId),
    // Detail-by-id: opening another conversation must never show this one's messages under that name.
    placeholderData: undefined,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  })

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'contact'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
  }

  // Showing an unread conversation IS reading it — the badge is about what nobody has looked at.
  const unread = data?.summary.unread ?? false
  const { mutate: markRead } = useMutation({
    mutationFn: () => adminContactApi.markRead(conversationId),
    onSuccess: invalidateAll,
  })
  useEffect(() => {
    if (unread) markRead()
  }, [unread, markRead])

  const replyMutation = useMutation({
    mutationFn: () => adminContactApi.reply(conversationId, draft.trim()),
    onSuccess: (result) => {
      setDraft('')
      setLastResult(result)
      invalidateAll()
    },
  })

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

  if (isLoading) return <div className="py-12 flex justify-center"><LoadingSpinner /></div>
  if (isError || !data) return <QueryError error={error} onRetry={() => refetch()} />

  const { summary, messages } = data

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

      <ol className="p-4 space-y-3 max-h-[60vh] overflow-y-auto" aria-label={t('contactMessages.messagesLabel')}>
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
              <p className="text-sm text-surface-100 whitespace-pre-wrap break-words">{m.body}</p>
            </div>
          </li>
        ))}
      </ol>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (draft.trim() && !replyMutation.isPending) replyMutation.mutate()
        }}
        className="p-4 border-t border-surface-800 space-y-3"
      >
        <label htmlFor="admin-contact-reply" className="block text-sm font-medium text-surface-300">
          {t('contactMessages.reply')}
        </label>
        <textarea
          id="admin-contact-reply"
          rows={4}
          maxLength={CONTACT_BODY_MAX}
          value={draft}
          onChange={(e) => { setDraft(e.target.value); setLastResult(null) }}
          className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-y"
        />
        {replyMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(replyMutation.error)}</p>
        )}
        {lastResult && (
          <p role="status" className={clsx('text-sm', lastResult.mailed ? 'text-green-400' : 'text-amber-400')}>
            {lastResult.mailed ? t('contactMessages.sentMailed') : t('contactMessages.sentInAppOnly')}
          </p>
        )}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-xs text-surface-500">
            {summary.userId ? t('contactMessages.replyHintUser') : t('contactMessages.replyHintGuest')}
          </p>
          <Button type="submit" loading={replyMutation.isPending} disabled={!draft.trim()}>
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
