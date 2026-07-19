import { Check, Copy, Lock, Scissors, Star } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

// Status-colored visual language for training entries, shared by week and month views.
function trainingColors(status: PersonalTraining['status']): string {
  switch (status) {
    case 'COMPLETED':
      return 'bg-green-500/25 border-green-500/60 text-green-300 hover:bg-green-500/40'
    case 'MISSED':
      return 'bg-rose-500/10 border-rose-500/40 text-rose-300/80 hover:bg-rose-500/20'
    default: // PLANNED
      return 'bg-indigo-500/25 border-indigo-500/60 text-indigo-300 hover:bg-indigo-500/40'
  }
}

interface TrainingBlockProps {
  training: PersonalTraining
  onClick: () => void
  style?: React.CSSProperties
  // Small variant for month-view chips
  compact?: boolean
  clampedTop?: boolean
  clampedBottom?: boolean
  // Week-view clipboard + drag&drop (both roles); all optional so the month view stays untouched
  onCopy?: () => void
  onCut?: () => void
  isCut?: boolean
  isCopied?: boolean
  onPointerDown?: (e: React.PointerEvent<HTMLElement>) => void
  onResizePointerDown?: (e: React.PointerEvent<HTMLElement>) => void
  isDragging?: boolean
  isLongPressing?: boolean
}

export function TrainingBlock({
  training, onClick, style, compact, clampedTop, clampedBottom,
  onCopy, onCut, isCut, isCopied, onPointerDown, onResizePointerDown, isDragging, isLongPressing,
}: TrainingBlockProps) {
  const { t } = useTranslation('training')

  const content = (
    <>
      {training.hasUnreadActivity && (
        <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-rose-500" />
      )}
      <span className="flex items-center gap-1 min-w-0">
        {training.status === 'COMPLETED' && <Check className="w-3 h-3 shrink-0" />}
        <span className="font-medium truncate">{training.title}</span>
      </span>
      {!compact && (
        <span className="block text-[10px] opacity-80">
          {clampedTop && '↑ '}
          {training.startTime.slice(0, 5)} - {training.endTime.slice(0, 5)}
          {clampedBottom && ' ↓'}
        </span>
      )}
    </>
  )

  if (compact) {
    return (
      <button
        onClick={onClick}
        style={style}
        className={clsx(
          // 'relative' (needed by the unread dot) must NOT coexist with 'absolute' —
          // whichever wins in the stylesheet breaks week-grid positioning
          'border rounded-md text-left transition-colors overflow-hidden',
          trainingColors(training.status),
          'relative w-full px-1.5 py-0.5 text-[11px] truncate block',
        )}
        title={training.title}
      >
        {content}
      </button>
    )
  }

  // Week variant: wrapper div carries the grid position so the inner button can share
  // the block with hover actions and the resize handle (same layout as the admin calendar)
  const draggable = !!onPointerDown
  return (
    <div
      style={style}
      onPointerDown={onPointerDown}
      className={clsx(
        'group absolute border rounded-md transition-colors overflow-hidden',
        trainingColors(training.status),
        draggable && !isDragging && 'cursor-grab',
        isDragging && 'opacity-40 cursor-grabbing',
        isCut && 'ring-2 ring-dashed ring-amber-400 opacity-60',
        isCopied && 'ring-2 ring-dashed ring-primary-400',
        isLongPressing && !isDragging && 'ring-2 ring-primary-400/60 z-30',
      )}
    >
      <button
        onClick={onClick}
        className={clsx('w-full h-full px-1.5 py-1 text-xs text-left', draggable && 'select-none')}
        title={training.title}
      >
        {content}
      </button>

      {(onCopy || onCut) && (
        <div
          data-admin-action
          // Always visible on touch (no hover there); hover-reveal only on pointer:fine devices
          // so mouse users keep a clean calendar. Solid chips with a real tap area — the old
          // 10px hover-only icons were easy to miss (click landed on the body → detail opened)
          className="absolute top-1 right-1 flex gap-1 z-20 opacity-100 transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100"
        >
          {onCopy && (
            <button
              data-admin-action
              onClick={(e) => { e.stopPropagation(); onCopy() }}
              className="p-1 rounded-md bg-surface-950/90 border border-surface-600 text-surface-200 shadow-sm hover:text-primary-300 hover:border-primary-400 transition-colors"
              title={t('clipboard.copy')}
              aria-label={t('clipboard.copy')}
            >
              <Copy className="w-3.5 h-3.5" />
            </button>
          )}
          {onCut && (
            <button
              data-admin-action
              onClick={(e) => { e.stopPropagation(); onCut() }}
              className="p-1 rounded-md bg-surface-950/90 border border-surface-600 text-surface-200 shadow-sm hover:text-amber-300 hover:border-amber-400 transition-colors"
              title={t('clipboard.cut')}
              aria-label={t('clipboard.cut')}
            >
              <Scissors className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      )}

      {onResizePointerDown && (
        <div
          data-admin-action
          className="absolute bottom-0 left-0 right-0 h-2 cursor-ns-resize z-20"
          style={{ touchAction: 'none' }}
          onPointerDown={onResizePointerDown}
        />
      )}
    </div>
  )
}

interface ReservationBlockProps {
  reservation: ReservationOverlayItem
  label: string
  onClick: () => void
  style?: React.CSSProperties
  compact?: boolean
}

interface InvitationBlockProps {
  invitation: InvitationOverlayItem
  label: string
  onClick: () => void
  style?: React.CSSProperties
  compact?: boolean
}

// Held seat the athlete has NOT booked yet: amber call-to-action with a pulsing dot —
// deliberately nothing like the calm gray reservation, so it cannot pass for "already booked".
export function InvitationBlock({ invitation, label, onClick, style, compact }: InvitationBlockProps) {
  const title = invitation.title || label
  return (
    <button
      onClick={onClick}
      style={style}
      className={clsx(
        'border rounded-md text-left transition-colors overflow-hidden',
        'bg-amber-500/20 border-amber-500/70 text-amber-300 hover:bg-amber-500/35',
        compact ? 'relative w-full px-1.5 py-0.5 text-[11px] truncate block' : 'absolute px-1.5 py-1 text-xs',
      )}
      title={`${label}: ${title}`}
    >
      <span className={clsx(
        'rounded-full bg-amber-400 animate-pulse',
        compact ? 'absolute top-1 right-1 w-2 h-2' : 'absolute top-1 right-1 w-2 h-2',
      )} />
      <span className="flex items-center gap-1 min-w-0">
        <Star className="w-3 h-3 shrink-0" />
        <span className="font-semibold truncate">{compact ? title : label}</span>
      </span>
      {!compact && (
        <span className="block text-[10px] opacity-90 truncate">
          {invitation.startTime && invitation.endTime
            ? `${invitation.startTime.slice(0, 5)} - ${invitation.endTime.slice(0, 5)} · ${title}`
            : title}
        </span>
      )}
    </button>
  )
}

// Read-only overlay of a confirmed booking from the public reservation system.
export function ReservationBlock({ reservation, label, onClick, style, compact }: ReservationBlockProps) {
  const title = reservation.title || label
  return (
    <button
      onClick={onClick}
      style={style}
      className={clsx(
        'border border-dashed rounded-md text-left transition-colors overflow-hidden',
        'bg-surface-700/40 border-surface-500/60 text-surface-300 hover:bg-surface-700/60',
        compact ? 'relative w-full px-1.5 py-0.5 text-[11px] truncate block' : 'absolute px-1.5 py-1 text-xs',
      )}
      title={title}
    >
      {reservation.isNew && (
        <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-rose-500" />
      )}
      <span className="flex items-center gap-1 min-w-0">
        <Lock className="w-3 h-3 shrink-0" />
        <span className="font-medium truncate">{title}</span>
      </span>
      {!compact && (
        <span className="block text-[10px] opacity-80">
          {reservation.startTime.slice(0, 5)} - {reservation.endTime.slice(0, 5)}
        </span>
      )}
    </button>
  )
}
