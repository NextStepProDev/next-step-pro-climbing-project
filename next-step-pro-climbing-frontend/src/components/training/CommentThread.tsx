import { useRef, useState, type ChangeEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Send, MessageSquare, Paperclip, X, Pencil } from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { PrivateImage, PrivateFileCard } from './PrivateFile'
import { isImageType } from '../../utils/mediaTypes'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { renderRichText } from '../../utils/renderRichText'
import { continueList, normalizeBulletMarker, type TextEdit } from '../../utils/richTextInput'
import { getErrorMessage } from '../../utils/errors'
import {
  ATTACHMENT_INPUT_TYPES,
  isImageInput,
  toUploadableImage,
  validateImageFile,
} from '../../utils/imageUtils'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { TrainingCommentFile, TrainingCommentItem } from '../../types'

/** Mirrors TrainingCommentFile.MAX_PER_COMMENT on the backend. */
const MAX_FILES = 3
/** Mirrors TrainingComment.MAX_BODY_LENGTH, which CreateTrainingCommentRequest enforces with @Size. */
const MAX_BODY = 1000
/** Rows the composer grows to before it starts scrolling — enough to see a short list whole. */
const MAX_ROWS = 6

/** Grows the box with the text: a one-row field building a five-item list is a peephole. */
function rowsFor(text: string, min: number): number {
  return Math.min(MAX_ROWS, Math.max(min, text.split('\n').length))
}

function applyTextEdit(ta: HTMLTextAreaElement, edit: TextEdit, setValue: (value: string) => void) {
  setValue(edit.value)
  const { caret } = edit
  requestAnimationFrame(() => {
    ta.focus()
    ta.setSelectionRange(caret, caret)
  })
}

/**
 * Carries a list on to the next item.
 *
 * Bound to Shift+Enter rather than Enter because in this thread Enter sends the message — the
 * rule belongs to whichever key actually breaks the line, which is why RichTextEditor (where
 * that key is plain Enter) wires the same helper to a different one. No toolbar here: the
 * bubble is too narrow for one, so the markers are typed and ⌘B/⌘I stay in the big forms.
 */
function carryListOnShiftEnter(
  e: React.KeyboardEvent<HTMLTextAreaElement>,
  setValue: (value: string) => void,
) {
  const ta = e.currentTarget
  const edit = continueList(ta.value, ta.selectionStart, ta.selectionEnd, MAX_BODY)
  if (!edit) return
  e.preventDefault()
  applyTextEdit(ta, edit, setValue)
}

/** Turns a just-typed `- ` / `* ` at a line start into the bullet the renderer draws. */
function handleMarkdownChange(
  e: ChangeEvent<HTMLTextAreaElement>,
  setValue: (value: string) => void,
) {
  const edit = normalizeBulletMarker(e.target.value, e.target.selectionStart)
  if (edit) {
    applyTextEdit(e.target, edit, setValue)
    return
  }
  setValue(e.target.value)
}

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
  const [staged, setStaged] = useState<File[]>([])
  const [pickError, setPickError] = useState<string | null>(null)
  const [picking, setPicking] = useState(false)
  // Which message is open for correction, and its working text. Both live HERE rather than inside
  // the bubble: the thread polls every 15 s, and state owned by the parent cannot be lost to a
  // re-render of the list — it also makes "at most one message is being edited" true by construction.
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const fileInput = useRef<HTMLInputElement>(null)

  const { data: comments, isLoading } = useQuery({
    queryKey: ['trainingCalendar', 'comments', trainingId],
    queryFn: () => api.getComments(trainingId),
    // Poll while the modal is open so the conversation feels live
    refetchInterval: 15_000,
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'comments', trainingId] })

  const postMutation = useMutation({
    // JSON when it is only words, multipart when anything is attached. The choice lives in the
    // adapter so this component never learns which role it is rendering for.
    mutationFn: ({ body, files }: { body: string; files: File[] }) =>
      files.length > 0
        ? api.addCommentWithFiles(trainingId, body || null, files)
        : api.addComment(trainingId, body),
    onSuccess: () => {
      setDraft('')
      setStaged([])
      invalidate()
      onPosted?.()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (fileId: string) => api.deleteCommentFile(fileId),
    onSuccess: invalidate,
  })

  const editMutation = useMutation({
    mutationFn: ({ commentId, body }: { commentId: string; body: string }) =>
      api.editComment(commentId, body),
    onSuccess: () => {
      setEditingId(null)
      setEditDraft('')
      invalidate()
    },
  })

  const startEdit = (comment: TrainingCommentItem) => {
    editMutation.reset()
    setEditingId(comment.id)
    // The backend escapes on write, so the stored text carries entities. Seeding the field with the
    // raw body would show "&quot;" and escape it a second time on save.
    setEditDraft(decodeHtmlEntities(comment.body ?? ''))
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditDraft('')
  }

  const saveEdit = (commentId: string) => {
    const body = editDraft.trim()
    // An edit corrects the text; it may never empty it. Clearing a message is not this button's job.
    if (!body || editMutation.isPending) return
    editMutation.mutate({ commentId, body })
  }

  const pickFiles = async (e: ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(e.target.files ?? [])
    // Reset at once, so picking the same file twice in a row still fires a change event.
    e.target.value = ''
    if (picked.length === 0) return

    if (staged.length + picked.length > MAX_FILES) {
      setPickError(t('comments.fileTooMany', { max: MAX_FILES }))
      return
    }

    setPickError(null)
    setPicking(true)
    try {
      const prepared: File[] = []
      for (const file of picked) {
        const invalid = validateImageFile(file, ATTACHMENT_INPUT_TYPES)
        if (invalid) {
          setPickError(invalid)
          return
        }
        // Shrunk and re-encoded here rather than server-side: it saves the upload, it is what
        // turns an iPhone HEIC into something the backend can read, and the canvas pass is also
        // what drops EXIF — so the location never leaves the device in the first place.
        prepared.push(isImageInput(file) ? await toUploadableImage(file) : file)
      }
      setStaged(current => [...current, ...prepared])
    } catch (err) {
      setPickError(getErrorMessage(err))
    } finally {
      setPicking(false)
    }
  }

  const send = () => {
    const body = draft.trim()
    if ((!body && staged.length === 0) || postMutation.isPending || picking) return
    postMutation.mutate({ body, files: staged })
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
            <CommentBubble
              key={comment.id}
              comment={comment}
              coachLabel={t('comments.coach')}
              onDeleteFile={(fileId) => deleteMutation.mutate(fileId)}
              edit={{
                editing: editingId === comment.id,
                draft: editDraft,
                saving: editMutation.isPending,
                onStart: () => startEdit(comment),
                onChange: setEditDraft,
                onCancel: cancelEdit,
                onSave: () => saveEdit(comment.id),
              }}
            />
          ))}
        </div>
      )}

      {postMutation.isError && (
        <p className="text-xs text-rose-400/80 mt-2">{getErrorMessage(postMutation.error)}</p>
      )}
      {deleteMutation.isError && (
        <p className="text-xs text-rose-400/80 mt-2">{getErrorMessage(deleteMutation.error)}</p>
      )}
      {editMutation.isError && (
        <p className="text-xs text-rose-400/80 mt-2">{getErrorMessage(editMutation.error)}</p>
      )}
      {pickError && (
        <p className="text-xs text-rose-400/80 mt-2" role="alert">{pickError}</p>
      )}

      {staged.length > 0 && (
        <ul className="flex flex-wrap gap-2 mt-3">
          {staged.map((file, index) => (
            <li
              key={`${file.name}-${index}`}
              className="flex items-center gap-2 pl-2 pr-1 py-1 rounded-md border border-surface-700 bg-surface-800 text-xs text-surface-200"
            >
              <span className="max-w-[10rem] truncate">{file.name}</span>
              <button
                type="button"
                onClick={() => setStaged(current => current.filter((_, i) => i !== index))}
                aria-label={t('comments.fileRemove')}
                className="p-0.5 rounded hover:bg-surface-700 text-surface-400"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="flex gap-2 mt-3">
        <input
          ref={fileInput}
          type="file"
          multiple
          accept="image/*,.heic,.heif,application/pdf"
          onChange={pickFiles}
          className="hidden"
        />
        <button
          type="button"
          onClick={() => fileInput.current?.click()}
          disabled={staged.length >= MAX_FILES || picking}
          aria-label={t('comments.fileAdd')}
          title={t('comments.fileAdd')}
          className="px-3 rounded-lg border border-surface-700 bg-surface-800 text-surface-300 hover:bg-surface-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <Paperclip className="w-4 h-4" />
        </button>
        <textarea
          value={draft}
          onChange={(e) => handleMarkdownChange(e, setDraft)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send()
            } else if (e.key === 'Enter') {
              carryListOnShiftEnter(e, setDraft)
            }
          }}
          placeholder={t('comments.placeholder')}
          rows={rowsFor(draft, 1)}
          maxLength={MAX_BODY}
          className="flex-1 bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-sm text-surface-100 resize-none"
        />
        <button
          onClick={send}
          disabled={(!draft.trim() && staged.length === 0) || postMutation.isPending || picking}
          aria-label={t('comments.send')}
          className="px-3 rounded-lg bg-primary-600 hover:bg-primary-500 disabled:opacity-40 disabled:cursor-not-allowed text-white transition-colors"
        >
          <Send className="w-4 h-4" />
        </button>
      </div>

      <p className="text-[11px] text-surface-500 mt-1.5">{t('comments.fileHint', { max: MAX_FILES })}</p>
    </div>
  )
}

interface BubbleEdit {
  editing: boolean
  draft: string
  saving: boolean
  onStart: () => void
  onChange: (value: string) => void
  onCancel: () => void
  onSave: () => void
}

function CommentBubble({
  comment,
  coachLabel,
  onDeleteFile,
  edit,
}: {
  comment: TrainingCommentItem
  coachLabel: string
  onDeleteFile: (fileId: string) => void
  edit: BubbleEdit
}) {
  const { t } = useTranslation('training')
  // Only the author, and only where there are words to correct: a message that is nothing but a
  // photo would have to grow a caption, which is a new message rather than a correction.
  const canEdit = comment.mine && comment.body !== null

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
        {/* Backend HTML-escapes on write, so decode before the renderer escapes again. Null when
            the message is only files. A div, not a p — lists and headings are block elements. */}
        {edit.editing ? (
          <div className="space-y-1.5">
            <textarea
              value={edit.draft}
              onChange={(e) => handleMarkdownChange(e, edit.onChange)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  edit.onSave()
                } else if (e.key === 'Enter') {
                  carryListOnShiftEnter(e, edit.onChange)
                } else if (e.key === 'Escape') {
                  e.preventDefault()
                  // stopPropagation, not just preventDefault: Modal listens for Escape on the
                  // document, so without this one keypress both cancels the edit AND slams the
                  // whole training modal shut — backing out of a correction would close the thread.
                  e.stopPropagation()
                  edit.onCancel()
                }
              }}
              rows={rowsFor(edit.draft, 2)}
              maxLength={MAX_BODY}
              autoFocus
              aria-label={t('comments.edit')}
              className="w-full bg-surface-900 border border-surface-700 rounded-lg px-2 py-1.5 text-sm text-surface-100 resize-none"
            />
            <div className="flex justify-end gap-2 text-[11px]">
              <button
                type="button"
                onClick={edit.onCancel}
                className="px-2 py-0.5 rounded text-surface-400 hover:text-surface-200"
              >
                {t('comments.editCancel')}
              </button>
              <button
                type="button"
                onClick={edit.onSave}
                disabled={!edit.draft.trim() || edit.saving}
                className="px-2 py-0.5 rounded bg-primary-600 hover:bg-primary-500 disabled:opacity-40 disabled:cursor-not-allowed text-white transition-colors"
              >
                {t('comments.editSave')}
              </button>
            </div>
          </div>
        ) : (
          comment.body && (
            <div
              className="text-sm text-surface-100 break-words whitespace-pre-wrap"
              dangerouslySetInnerHTML={{ __html: renderRichText(decodeHtmlEntities(comment.body)) }}
            />
          )
        )}
        {comment.files.length > 0 && (
          <div className={clsx('space-y-1.5', comment.body && 'mt-2')}>
            {comment.files.map((file) => (
              <CommentAttachment key={file.id} file={file} onDelete={() => onDeleteFile(file.id)} />
            ))}
          </div>
        )}
        <div className="flex items-center justify-end gap-1.5 text-[10px] text-surface-500 mt-0.5">
          {canEdit && !edit.editing && (
            <button
              type="button"
              onClick={edit.onStart}
              aria-label={t('comments.edit')}
              title={t('comments.edit')}
              // Always visible rather than revealed on hover: on a touch screen a hover-only
              // affordance is one that never appears.
              className="p-0.5 -m-0.5 rounded text-surface-500 hover:text-surface-300 transition-colors"
            >
              <Pencil className="w-3 h-3" />
            </button>
          )}
          {/* The thread is the only record of what was agreed, so a rewrite has to say so. */}
          {comment.editedAt && <span>{t('comments.edited')}</span>}
          <span>{format(new Date(comment.createdAt), 'dd.MM HH:mm')}</span>
        </div>
      </div>
    </div>
  )
}

function CommentAttachment({ file, onDelete }: { file: TrainingCommentFile; onDelete: () => void }) {
  const { t } = useTranslation('training')

  const expiry = (
    <p className="text-[10px] text-surface-500">
      {t('comments.fileExpires', { date: format(new Date(file.expiresAt), 'dd.MM.yyyy') })}
    </p>
  )

  const remove = file.canDelete ? (
    <button
      type="button"
      onClick={onDelete}
      className="text-[11px] text-rose-300/80 hover:text-rose-200 underline underline-offset-2"
    >
      {t('comments.fileDelete')}
    </button>
  ) : null

  if (isImageType(file.mimeType)) {
    return (
      <div className="space-y-1">
        <PrivateImage
          url={file.url}
          alt={file.fileName ?? t('comments.fileAlt')}
          width={file.width}
          height={file.height}
          lightboxExtra={
            <div className="flex items-center justify-between gap-3">
              {expiry}
              {remove}
            </div>
          }
        />
        {expiry}
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <PrivateFileCard url={file.url} fileName={file.fileName} sizeBytes={file.sizeBytes} />
      <div className="flex items-center justify-between gap-3">
        {expiry}
        {remove}
      </div>
    </div>
  )
}
