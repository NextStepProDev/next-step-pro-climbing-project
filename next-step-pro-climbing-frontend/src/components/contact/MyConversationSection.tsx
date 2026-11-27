import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
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
import { ChatScroller } from './ChatScroller'
import { useChatWindow } from './useChatWindow'
import { useStagedChatFiles } from './useStagedChatFiles'
import { ChatAttachPicker, ChatMessageFiles } from './ChatAttachments'
import { ClearMessageButton } from './UnsentMessage'
import { RichTextEditor } from '../ui/RichTextEditor'
import { renderRichText } from '../../utils/renderRichText'
import { readReceipt, type ChatMessage } from '../../utils/chatMessages'
import type { ConversationMessage } from '../../types'

/** In the person's chat, "own" is what they wrote: sending one scrolls down to it. */
const isOwnMessage = (m: ChatMessage) => !(m as ConversationMessage).fromStaff

/**
 * The signed-in person's one conversation with the team: every message they sent from the
 * contact page, a course or the calendar, and every answer, in order. Writing here goes into the
 * same conversation as the forms do.
 */
export function MyConversationSection({ onDirtyChange }: {
  /** Reported during render (see useChildDirty): the page asks before a tab switch drops it. */
  onDirtyChange?: (dirty: boolean) => void
} = {}) {
  const { t, i18n } = useTranslation('reservations')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')

  // A chat window: the newest page, older pages on scroll, and a 5 s poll for only what is new.
  // The badge elsewhere keeps its minute.
  const chat = useChatWindow({
    latestKey: ['conversation'],
    fetchLatest: contactApi.getConversation,
    fetchOlder: contactApi.getOlder,
    fetchNewer: contactApi.getNewer,
  })
  const messages = chat.messages
  // The newest poll knows best: the mark moves while the chat stays open
  const teamReadAt = chat.lastPoll ? chat.lastPoll.teamReadAt : chat.latest?.teamReadAt
  const receipt = readReceipt(messages, isOwnMessage, teamReadAt)

  const seenMutation = useMutation({
    mutationFn: contactApi.markSeen,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['conversation', 'unseen'] }),
  })
  // Showing an unseen answer IS seeing it. Fired from an effect on the data, not on mount: an
  // answer can come in on a poll while the tab is already open. ⚠️ Keyed on the team's newest
  // message, not on the flag: a poll answered before the last mark-seen still says "unseen", and a
  // new answer says it again — the flag alone never changes in between, so the dot would stay lit.
  const unseen = chat.lastPoll?.unseenReply ?? chat.latest?.unseenReply ?? false
  const newestFromTeam = [...messages].reverse().find((m) => m.fromStaff)?.id ?? 'unseen'
  const unseenMarker = unseen ? newestFromTeam : null
  const { mutate: markSeen } = seenMutation
  useEffect(() => {
    if (unseenMarker) markSeen()
  }, [unseenMarker, markSeen])

  const attachments = useStagedChatFiles()
  const { addMessage, hideFile, pollNow } = chat

  // Text alone goes by the plain endpoint (outside the upload rate limit); with files, multipart.
  const sendMutation = useMutation({
    mutationFn: async () => {
      const body = draft.trim()
      if (attachments.staged.length > 0) {
        return contactApi.sendWithFiles(body || null, i18n.language, attachments.staged)
      }
      await contactApi.send({ body, consent: false, contextType: 'GENERAL', language: i18n.language })
      return null
    },
    onSuccess: (message) => {
      setDraft('')
      attachments.clear()
      // The multipart endpoint echoes the message; the plain one does not (it serves the signed-out
      // form too), so then ask for news at once rather than waiting up to a poll.
      if (message) addMessage(message)
      else pollNow()
    },
  })

  const deleteFileMutation = useMutation({
    mutationFn: (fileId: string) => contactApi.deleteFile(fileId),
    onSuccess: (_, fileId) => hideFile(fileId),
  })

  const hasWriting = draft.trim().length > 0 || attachments.staged.length > 0
  const canSend = hasWriting && !attachments.picking
  onDirtyChange?.(hasWriting)

  if (chat.isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (chat.isError) return <QueryError error={chat.error} onRetry={() => chat.refetch()} />

  return (
    <div className="space-y-6">
      {messages.length === 0 ? (
        <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 text-center">
          <MessageSquare className="w-8 h-8 mx-auto text-surface-600 mb-3" aria-hidden="true" />
          <p className="text-surface-200 font-medium">{t('messages.emptyTitle')}</p>
          <p className="text-sm text-surface-400 mt-1">{t('messages.emptyBody')}</p>
        </div>
      ) : (
        <>
        {chat.olderError != null && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(chat.olderError)}</p>
        )}
        <ChatScroller
          messages={messages}
          hasOlder={chat.hasOlder}
          loadingOlder={chat.loadingOlder}
          onLoadOlder={chat.loadOlder}
          isOwn={isOwnMessage}
          loadOlderLabel={t('messages.loadOlder')}
          newBelowLabel={t('messages.newBelow')}
          className="max-h-[60vh] overflow-y-auto pr-1"
        >
        <ol className="space-y-3" aria-label={t('messages.listLabel')}>
          {messages.map((m) => (
            <li key={m.id} className={clsx('flex flex-col', m.fromStaff ? 'items-start' : 'items-end')}>
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
                {/* A div, never a p: the renderer returns lists, which a p cannot hold. The body is stored
                    unescaped, and the renderer escapes it itself. */}
                {m.body !== null && (
                  <div
                    className="text-sm text-surface-100 whitespace-pre-wrap break-words"
                    dangerouslySetInnerHTML={{ __html: renderRichText(m.body) }}
                  />
                )}
                <ChatMessageFiles files={m.files} onDelete={(fileId) => deleteFileMutation.mutate(fileId)} />
              </div>
              {receipt?.messageId === m.id && (
                <p className="mt-1 text-[11px] text-surface-500">
                  {receipt.read && teamReadAt
                    ? t('messages.readByTeam', { time: format(new Date(teamReadAt), 'd MMM yyyy, HH:mm', { locale }) })
                    : t('messages.notReadYet')}
                </p>
              )}
            </li>
          ))}
        </ol>
        </ChatScroller>
        </>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (canSend && !sendMutation.isPending) sendMutation.mutate()
        }}
        className="bg-surface-900 border border-surface-800 rounded-xl p-4 space-y-3"
      >
        <label htmlFor="conversation-reply" className="block text-sm font-medium text-surface-300">
          {messages.length === 0 ? t('messages.writeFirst') : t('messages.reply')}
        </label>
        <RichTextEditor
          id="conversation-reply"
          rows={6}
          autoGrow
          maxLength={CONTACT_BODY_MAX}
          value={draft}
          onChange={setDraft}
        />
        <ChatAttachPicker
          staged={attachments.staged}
          picking={attachments.picking}
          error={attachments.error}
          onPick={attachments.pick}
          onRemove={attachments.remove}
          disabled={sendMutation.isPending}
        />
        {sendMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(sendMutation.error)}</p>
        )}
        {deleteFileMutation.isError && (
          <p role="alert" className="text-sm text-rose-400">{getErrorMessage(deleteFileMutation.error)}</p>
        )}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-xs text-surface-500">
            {t('messages.hint')}{' '}
            <Link to="/kontakt" className="underline hover:text-surface-300">{t('messages.otherWays')}</Link>
          </p>
          <div className="flex items-center justify-end gap-2">
            {hasWriting && (
              <ClearMessageButton
                disabled={sendMutation.isPending}
                onClear={() => {
                  setDraft('')
                  attachments.clear()
                }}
              />
            )}
            <Button type="submit" loading={sendMutation.isPending} disabled={!canSend}>
              {t('messages.send')}
            </Button>
          </div>
        </div>
      </form>
    </div>
  )
}
