import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { MessageSquare } from 'lucide-react'
import clsx from 'clsx'
import { contactApi } from '../../api/client'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { getErrorMessage } from '../../utils/errors'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { QueryError } from '../ui/QueryError'
import { ContextChip } from './ContextChip'
import { CONTACT_BODY_MAX } from './ContactForm'

/**
 * The signed-in person's one conversation with the team: every message they sent from the
 * contact page, a course or the calendar, and every answer, in order. Writing here goes into the
 * same conversation as the forms do.
 */
export function MyConversationSection() {
  const { t, i18n } = useTranslation('reservations')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['conversation'],
    queryFn: contactApi.getConversation,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  })

  const seenMutation = useMutation({
    mutationFn: contactApi.markSeen,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['conversation', 'unseen'] }),
  })
  // Showing an unseen answer IS seeing it. Fired from an effect on the data, not on mount: the
  // conversation can arrive (or an answer can come in on a poll) while the tab is already open.
  const unseen = data?.unseenReply ?? false
  const { mutate: markSeen } = seenMutation
  useEffect(() => {
    if (unseen) markSeen()
  }, [unseen, markSeen])

  const sendMutation = useMutation({
    mutationFn: () => contactApi.send({
      body: draft.trim(),
      consent: false,
      contextType: 'GENERAL',
      language: i18n.language,
    }),
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey: ['conversation'] })
    },
  })

  if (isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (isError) return <QueryError error={error} onRetry={() => refetch()} />

  const messages = data?.messages ?? []

  return (
    <div className="space-y-6">
      {messages.length === 0 ? (
        <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 text-center">
          <MessageSquare className="w-8 h-8 mx-auto text-surface-600 mb-3" aria-hidden="true" />
          <p className="text-surface-200 font-medium">{t('messages.emptyTitle')}</p>
          <p className="text-sm text-surface-400 mt-1">{t('messages.emptyBody')}</p>
        </div>
      ) : (
        <ol className="space-y-3" aria-label={t('messages.listLabel')}>
          {messages.map((m) => (
            <li key={m.id} className={clsx('flex', m.fromStaff ? 'justify-start' : 'justify-end')}>
              <div
                className={clsx(
                  'max-w-[85%] sm:max-w-[75%] rounded-2xl px-4 py-3 border',
                  m.fromStaff
                    ? 'bg-surface-900 border-surface-700 rounded-bl-sm'
                    : 'bg-primary-600/15 border-primary-500/30 rounded-br-sm',
                )}
              >
                <p className="text-[11px] text-surface-500 mb-1">
                  {m.fromStaff ? t('messages.fromTeam') : t('messages.fromMe')}
                  {' · '}
                  {format(new Date(m.createdAt), 'd MMM yyyy, HH:mm', { locale })}
                </p>
                {!m.fromStaff && m.contextType !== 'GENERAL' && (
                  <div className="mb-1">
                    <ContextChip stored={m} compact />
                  </div>
                )}
                <p className="text-sm text-surface-100 whitespace-pre-wrap break-words">{m.body}</p>
              </div>
            </li>
          ))}
        </ol>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (draft.trim() && !sendMutation.isPending) sendMutation.mutate()
        }}
        className="bg-surface-900 border border-surface-800 rounded-xl p-4 space-y-3"
      >
        <label htmlFor="conversation-reply" className="block text-sm font-medium text-surface-300">
          {messages.length === 0 ? t('messages.writeFirst') : t('messages.reply')}
        </label>
        <textarea
          id="conversation-reply"
          rows={4}
          maxLength={CONTACT_BODY_MAX}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-y"
        />
        {sendMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(sendMutation.error)}</p>
        )}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-xs text-surface-500">
            {t('messages.hint')}{' '}
            <Link to="/kontakt" className="underline hover:text-surface-300">{t('messages.otherWays')}</Link>
          </p>
          <Button type="submit" loading={sendMutation.isPending} disabled={!draft.trim()}>
            {t('messages.send')}
          </Button>
        </div>
      </form>
    </div>
  )
}
