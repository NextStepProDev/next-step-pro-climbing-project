import { Check, Lock, Star } from 'lucide-react'
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
}

export function TrainingBlock({ training, onClick, style, compact, clampedTop, clampedBottom }: TrainingBlockProps) {
  return (
    <button
      onClick={onClick}
      style={style}
      className={clsx(
        // 'relative' (needed by the unread dot) must NOT coexist with 'absolute' —
        // whichever wins in the stylesheet breaks week-grid positioning
        'border rounded-md text-left transition-colors overflow-hidden',
        trainingColors(training.status),
        compact ? 'relative w-full px-1.5 py-0.5 text-[11px] truncate block' : 'absolute px-1.5 py-1 text-xs',
      )}
      title={training.title}
    >
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
    </button>
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
        compact ? 'w-full px-1.5 py-0.5 text-[11px] truncate block' : 'absolute px-1.5 py-1 text-xs',
      )}
      title={title}
    >
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
