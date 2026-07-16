import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Send, MessageSquare } from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { getErrorMessage } from '../../utils/errors'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { TrainingCommentItem } from '../../types'

interface CommentThreadProps {
  trainingId: string
  api: TrainingCalendarAdapter
  // Invalidated after posting so unread badges on the other side stay honest
  onPosted?: () => void
}

// Chat-like athlete <-> coach thread of a single training.
export function CommentThread({ trainingId, api, onPosted }: CommentThreadProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')

  const { data: comments, isLoading } = useQuery({
    queryKey: ['trainingCalendar', 'comments', trainingId],
    queryFn: () => api.getComments(trainingId),
    // Poll while the modal is open so the conversation feels live
    refetchInterval: 15_000,
  })

  const postMutation = useMutation({
    mutationFn: (body: string) => api.addComment(trainingId, body),
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'comments', trainingId] })
      onPosted?.()
    },
  })

  const send = () => {
    const body = draft.trim()
    if (body && !postMutation.isPending) postMutation.mutate(body)
  }

  return (
    <div>
      <h4 className="flex items-center gap-2 text-sm font-semibold text-surface-300 mb-2">
        <MessageSquare className="w-4 h-4" />
        {t('comments.title')}
      </h4>

      {isLoading ? (
        <div className="py-4 flex justify-center"><LoadingSpinner /></div>
      ) : !comments || comments.length === 0 ? (
        <p className="text-sm text-surface-500 py-2">{t('comments.empty')}</p>
      ) : (
        <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
          {comments.map((comment) => (
            <CommentBubble key={comment.id} comment={comment} coachLabel={t('comments.coach')} />
          ))}
        </div>
      )}

      {postMutation.isError && (
        <p className="text-xs text-rose-400/80 mt-2">{getErrorMessage(postMutation.error)}</p>
      )}

      <div className="flex gap-2 mt-3">
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send()
            }
          }}
          placeholder={t('comments.placeholder')}
          rows={1}
          maxLength={1000}
          className="flex-1 bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-sm text-surface-100 resize-none"
        />
        <button
          onClick={send}
          disabled={!draft.trim() || postMutation.isPending}
          aria-label={t('comments.send')}
          className="px-3 rounded-lg bg-primary-600 hover:bg-primary-500 disabled:opacity-40 disabled:cursor-not-allowed text-white transition-colors"
        >
          <Send className="w-4 h-4" />
        </button>
      </div>
    </div>
  )
}

function CommentBubble({ comment, coachLabel }: { comment: TrainingCommentItem; coachLabel: string }) {
  return (
    <div className={clsx('flex', comment.mine ? 'justify-end' : 'justify-start')}>
      <div
        className={clsx(
          'max-w-[85%] rounded-xl px-3 py-2',
          comment.mine
            ? 'bg-primary-600/30 border border-primary-500/30'
            : 'bg-surface-800 border border-surface-700',
        )}
      >
        {!comment.mine && (
          <div className="text-[11px] font-medium text-surface-400 mb-0.5">
            {comment.authorIsAdmin ? coachLabel : comment.authorName}
          </div>
        )}
        {/* Backend HTML-escapes on write; render as plain text */}
        <p className="text-sm text-surface-100 whitespace-pre-wrap break-words">{decodeHtmlEntities(comment.body)}</p>
        <div className="text-[10px] text-surface-500 mt-0.5 text-right">
          {format(new Date(comment.createdAt), 'dd.MM HH:mm')}
        </div>
      </div>
    </div>
  )
}
