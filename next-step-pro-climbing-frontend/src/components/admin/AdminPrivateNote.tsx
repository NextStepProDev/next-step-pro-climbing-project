import { useCallback, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { NotebookPen, Pencil, Trash2, Lock } from 'lucide-react'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { RichTextEditor } from '../ui/RichTextEditor'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { renderRichText } from '../../utils/renderRichText'
import type { AdminNoteTarget } from '../../types'

const MAX_BODY_LENGTH = 4000

interface AdminPrivateNoteProps {
  target: AdminNoteTarget
  targetId: string
}

/**
 * The owner's private note about one session — a slot, an event, or an entry in an athlete's
 * training calendar.
 *
 * ONE component for all three call sites (the same reason SlotKindPicker serves three forms):
 * a change to how notes behave is one edit, not three. It owns its own query and mutations, so
 * the host modal passes nothing but an address.
 *
 * The note is fetched from its own endpoint rather than riding along in the slot/event/training
 * payload, and that is what makes it private: those payloads are shared with clients, athletes
 * and the anonymous calendar cache. Callers must still gate on the admin role — this component
 * would happily render its "add a note" button for anybody, and the 403 would come too late to
 * be good UX.
 */
export function AdminPrivateNote({ target, targetId }: AdminPrivateNoteProps) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [expanded, setExpanded] = useState(false)
  const [isClamped, setIsClamped] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const queryKey = ['admin', 'notes', target, targetId]
  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => adminApi.getPrivateNote(target, targetId),
  })

  // The whole ['admin','notes'] prefix, not just this note's key: the calendar markers live under
  // it too, so writing here lights the marker up without a manual refresh.
  const refreshNotes = () => queryClient.invalidateQueries({ queryKey: ['admin', 'notes'] })

  const body = data?.body ?? null
  // Baseline is the loaded note, which arrives after mount — hence a plain comparison rather than
  // useDirty, whose snapshot would freeze on the empty first render and call everything dirty.
  const isDirty = draft.trim() !== (body ?? '')

  const saveMutation = useMutation({
    mutationFn: () => adminApi.savePrivateNote(target, targetId, draft.trim()),
    onSuccess: () => {
      setEditing(false)
      refreshNotes()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => adminApi.deletePrivateNote(target, targetId),
    onSuccess: () => {
      setConfirmDelete(false)
      setEditing(false)
      setDraft('')
      refreshNotes()
    },
  })

  const startEditing = () => {
    setDraft(body ?? '')
    setEditing(true)
  }

  /**
   * Whether the clamp is actually hiding anything, measured rather than guessed.
   *
   * A character count cannot answer this: the clamp counts HEIGHT, so a ten-line bulleted note
   * of 200 characters is cut off while any sensible length threshold says it is short — and
   * then the last four lines are unreachable, with no control offered to reveal them.
   */
  const measureClamp = useCallback((el: HTMLDivElement | null) => {
    if (!el || expanded) return
    setIsClamped(el.scrollHeight > el.clientHeight + 1)
  }, [expanded])

  if (isLoading) return null

  return (
    <div className="mt-4 rounded-lg border border-amber-500/25 bg-amber-500/5 p-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-2 text-sm font-medium text-surface-200">
          <NotebookPen className="w-4 h-4 text-amber-500" />
          {t('privateNote.title')}
        </span>
        {body !== null && !editing && (
          <div className="flex items-center gap-1">
            <Button size="sm" variant="ghost" onClick={startEditing}>
              <Pencil className="w-3.5 h-3.5 mr-1" />
              {t('privateNote.edit')}
            </Button>
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              aria-label={t('privateNote.delete')}
              className="p-1.5 rounded text-rose-400/70 hover:text-rose-400 transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          </div>
        )}
      </div>

      <p className="flex items-start gap-1.5 text-xs text-surface-400">
        <Lock className="w-3 h-3 mt-0.5 shrink-0" />
        {t('privateNote.onlyYouHint')}
      </p>

      {editing ? (
        <div className="space-y-2">
          <RichTextEditor
            value={draft}
            onChange={setDraft}
            maxLength={MAX_BODY_LENGTH}
            rows={6}
            autoFocus
            placeholder={t('privateNote.placeholder')}
            inputClassName="w-full bg-surface-800 border border-surface-600 rounded-b px-3 py-2 text-sm text-surface-100 resize-y focus:outline-none focus:border-primary-500"
          />
          {saveMutation.isError && (
            <p className="text-sm text-rose-400/80">{getErrorMessage(saveMutation.error)}</p>
          )}
          <div className="flex justify-end gap-2">
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
              {t('privateNote.cancel')}
            </Button>
            <Button
              size="sm"
              variant="primary"
              onClick={() => saveMutation.mutate()}
              loading={saveMutation.isPending}
              disabled={!isDirty || draft.trim().length === 0}
            >
              {t('privateNote.save')}
            </Button>
          </div>
        </div>
      ) : body === null ? (
        <Button size="sm" variant="secondary" onClick={startEditing}>
          {t('privateNote.add')}
        </Button>
      ) : (
        <div className="space-y-1">
          <div
            ref={measureClamp}
            // max-height rather than line-clamp: the note now renders lists and headings, and
            // line-clamp works by making the box a -webkit-box, which does not clamp block
            // children — a bulleted note would have run past the cut with no way to tell.
            // The note is the author's own text and is never HTML-escaped on write, so it goes
            // to the renderer as-is (it escapes) rather than through decodeHtmlEntities.
            className={`text-sm text-surface-200 whitespace-pre-wrap ${expanded ? '' : 'max-h-36 overflow-hidden'}`}
            dangerouslySetInnerHTML={{ __html: renderRichText(body) }}
          />
          {(expanded || isClamped) && (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="text-xs text-primary-400 hover:text-primary-300 transition-colors"
            >
              {expanded ? t('privateNote.showLess') : t('privateNote.showAll')}
            </button>
          )}
          {data?.updatedAt && (
            // An instant, so the device zone is the right frame to render it in.
            <p className="text-xs text-surface-500">
              {t('privateNote.lastEdited', { date: format(new Date(data.updatedAt), 'dd.MM.yyyy HH:mm') })}
            </p>
          )}
        </div>
      )}

      <ConfirmModal
        isOpen={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => deleteMutation.mutate()}
        title={t('privateNote.delete')}
        message={t('privateNote.deleteConfirm')}
        variant="danger"
      />
    </div>
  )
}
