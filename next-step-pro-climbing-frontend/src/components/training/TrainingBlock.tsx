import { Check, ClipboardList, Copy, Gauge, Lock, NotebookPen, Paperclip, Scissors, Star, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

/**
 * How much of an entry to show. Three densities, because there are three places an entry
 * is drawn and they have genuinely different budgets:
 *
 * - `chip`  — the week view's all-day lane. Title only, a couple of lines tall.
 * - `tile`  — the month grid. One line: title, hour, and the signals you scan a plan for.
 * - `full`  — the week view's hour grid and the day sheet. Everything, plus the clipboard
 *             controls and the drag/resize handles.
 */
export type BlockDensity = 'chip' | 'tile' | 'full'

// Status-colored visual language for training entries, used by the chip and full variants.
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

/**
 * On a month tile the status moves to the left edge and the card itself stays neutral.
 * A full colour wash reads fine as one big block on the week's hour grid, but four washed
 * cards stacked in an 8rem cell turn the day into a smear — the tint has to carry one
 * signal, not fill the tile.
 */
function trainingTileBorder(status: PersonalTraining['status']): string {
  switch (status) {
    case 'COMPLETED':
      return 'border-l-green-500'
    case 'MISSED':
      return 'border-l-rose-500'
    default: // PLANNED
      return 'border-l-indigo-400'
  }
}

// Shared chrome for every month tile: one line, a status bar down the left, room for a
// 24px tap target. Kind is carried by the border STYLE (solid vs dashed) so it never has
// to compete with the status for the colour channel.
const TILE_BASE = 'relative w-full flex items-center gap-1 min-h-6 px-1.5 py-1 rounded-r-md border-l-4 text-left text-[11px] overflow-hidden'

interface TrainingBlockProps {
  training: PersonalTraining
  onClick: () => void
  style?: React.CSSProperties
  density?: BlockDensity
  clampedTop?: boolean
  clampedBottom?: boolean
  // Week-view clipboard + drag&drop (both roles); all optional so the month view stays untouched
  onCopy?: () => void
  onCut?: () => void
  /**
   * Tile density only (the day sheet). Cleaning up a run of mistakes should not mean opening
   * each card in turn — but the chip and the hour grid do NOT get this: a third micro-button
   * there is the very thing that made an accidental copy so easy in the first place.
   */
  onDelete?: () => void
  isCut?: boolean
  isCopied?: boolean
  onPointerDown?: (e: React.PointerEvent<HTMLElement>) => void
  onResizePointerDown?: (e: React.PointerEvent<HTMLElement>) => void
  isDragging?: boolean
  isLongPressing?: boolean
}

/**
 * An entry is ALWAYS a control, armed clipboard or not.
 *
 * It used to stop being one while the clipboard was armed, so that a click would fall through
 * to the day cell and paste. That made every entry on screen a paste target — and the calendar
 * a mode with one exit: an athlete who armed the clipboard by accident found that each further
 * tap produced another copy and opened nothing she could delete. Pasting now happens only where
 * the "paste here" strip says it does (and on empty hour-grid space, where the click carries an
 * hour). Clicking an entry means what it looks like it means.
 */
function renderBlock(
  props: { className: string; title: string; onClick: () => void; style?: React.CSSProperties },
  children: React.ReactNode,
) {
  return (
    <button onClick={props.onClick} className={props.className} title={props.title} style={props.style}>
      {children}
    </button>
  )
}

export function TrainingBlock({
  training, onClick, style, density = 'full', clampedTop, clampedBottom,
  onCopy, onCut, onDelete, isCut, isCopied, onPointerDown, onResizePointerDown, isDragging, isLongPressing,
}: TrainingBlockProps) {
  const { t } = useTranslation('training')
  const isTask = training.kind === 'TASK'

  if (density === 'tile') {
    const tileClass = clsx(
      TILE_BASE,
      'bg-surface-800/70 text-surface-200',
      'hover:bg-surface-800 transition-colors',
      trainingTileBorder(training.status),
      // A dashed outline for a task. The left border still carries the status, so the two say
      // different things and neither has to give up its colour to the other.
      isTask && 'border-dashed',
      isCut && 'opacity-50',
      isCopied && 'ring-1 ring-primary-400/60',
    )
    const tileBody = (
      <>
        {isTask && <ClipboardList className="w-3 h-3 shrink-0 text-sky-400" aria-label={t('form.kind.TASK')} />}
        {training.status === 'COMPLETED' && <Check className="w-3 h-3 shrink-0 text-green-400" />}
        <span className="flex-1 truncate font-medium">{training.title}</span>
        {/* "≤ 2200" rather than a sentence: it has to survive a tile two words wide */}
        {training.targetCalories != null && (
          <span className="shrink-0 text-[10px] tabular-nums text-sky-300" title={t('form.calories')}>
            ≤ {training.targetCalories}
          </span>
        )}
        {/* An untimed training renders nothing here. Both times NULL is the default case
            in this domain, so a dash on every tile would be pure noise. */}
        {training.startTime && (
          <span className="shrink-0 text-[10px] tabular-nums text-surface-400">
            {training.startTime.slice(0, 5)}
          </span>
        )}
        {training.rpe != null && (
          <span
            className="shrink-0 inline-flex items-center gap-px text-[10px] text-surface-300"
            aria-label={`RPE ${training.rpe}`}
          >
            <Gauge className="w-2.5 h-2.5" />{training.rpe}
          </span>
        )}
        {training.attachments.length > 0 && (
          <Paperclip className="w-3 h-3 shrink-0 text-surface-400" aria-label={t('detail.materials')} />
        )}
        {/* Coach only (the flag is stamped on in coach view and never sent by the API). It joins
            the trailing signal row rather than taking the left edge or the border style — those
            two channels are already spoken for by status and kind. */}
        {training.hasPrivateNote && (
          <NotebookPen className="w-3 h-3 shrink-0 text-amber-500" aria-label={t('privateNote.marker')} />
        )}
        {training.hasUnreadActivity && (
          <span className="shrink-0 w-2 h-2 rounded-full bg-rose-500" />
        )}
      </>
    )

    // With any row action (the day sheet: copy, cut, delete) the row has to become a container
    // so the controls are siblings of the body button rather than buttons nested inside one.
    // The month grid passes no handler at all and keeps the cheap single-element tile.
    if (onCopy || onCut || onDelete) {
      return (
        <div className={tileClass}>
          <button onClick={onClick} className="flex-1 flex items-center gap-1 min-w-0 text-left" title={training.title}>
            {tileBody}
          </button>
          {onCopy && (
            <button
              onClick={(e) => { e.stopPropagation(); onCopy() }}
              className="shrink-0 p-1 rounded-md border border-surface-600 bg-surface-950/80 text-surface-200 hover:text-primary-300 hover:border-primary-400 transition-colors"
              title={t('clipboard.copy')}
              aria-label={t('clipboard.copy')}
            >
              <Copy className="w-3.5 h-3.5" />
            </button>
          )}
          {onCut && (
            <button
              onClick={(e) => { e.stopPropagation(); onCut() }}
              className="shrink-0 p-1 rounded-md border border-surface-600 bg-surface-950/80 text-surface-200 hover:text-amber-300 hover:border-amber-400 transition-colors"
              title={t('clipboard.cut')}
              aria-label={t('clipboard.cut')}
            >
              <Scissors className="w-3.5 h-3.5" />
            </button>
          )}
          {onDelete && (
            <button
              onClick={(e) => { e.stopPropagation(); onDelete() }}
              className="shrink-0 p-1 rounded-md border border-surface-600 bg-surface-950/80 text-surface-200 hover:text-rose-300 hover:border-rose-400 transition-colors"
              title={t('detail.delete')}
              aria-label={t('detail.delete')}
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      )
    }

    return renderBlock({ className: tileClass, title: training.title, onClick }, tileBody)
  }

  const content = (
    <>
      {training.hasUnreadActivity && (
        <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-rose-500" />
      )}
      <span className="flex items-center gap-1 min-w-0">
        {isTask && <ClipboardList className="w-3 h-3 shrink-0 text-sky-400" aria-label={t('form.kind.TASK')} />}
        {training.status === 'COMPLETED' && <Check className="w-3 h-3 shrink-0" />}
        {training.hasPrivateNote && (
          <NotebookPen className="w-3 h-3 shrink-0 text-amber-500" aria-label={t('privateNote.marker')} />
        )}
        {/* The chip is two lines tall now, so it can afford to wrap instead of cutting a title
            off mid-word in a ~120px day column. The hour grid keeps one truncated line: there
            the height is the entry's duration, not something the text may spend. */}
        <span className={clsx('font-medium min-w-0', density === 'chip' ? 'line-clamp-2' : 'truncate')}>
          {training.title}
        </span>
      </span>
      {density === 'full' && training.targetCalories != null && (
        <span className="inline-flex items-center mt-0.5 px-1 py-px rounded bg-sky-500/15 text-[9px] font-medium text-sky-300">
          ≤ {training.targetCalories} kcal
        </span>
      )}
      {density === 'full' && training.startTime && training.endTime && (
        <span className="block text-[10px] opacity-80">
          {clampedTop && '↑ '}
          {training.startTime.slice(0, 5)} - {training.endTime.slice(0, 5)}
          {clampedBottom && ' ↓'}
        </span>
      )}
    </>
  )

  if (density === 'chip') {
    const chipBase = clsx(
      // 'relative' (needed by the unread dot) must NOT coexist with 'absolute' —
      // whichever wins in the stylesheet breaks week-grid positioning
      'relative w-full min-h-12 border rounded-md overflow-hidden text-left text-[11px] transition-colors',
      trainingColors(training.status),
      isCut && 'opacity-50',
      isCopied && 'ring-1 ring-primary-400/60',
    )

    /* Clipboard controls as an OVERLAY, not a row of their own.
       ⚠️ A row made the chip a container with a hole in it: the row spanned the full width but
       held its buttons at the end, so the click on its empty half found no <button>, fell
       through to the day cell and opened the create form — tapping an entry answered with
       "New entry". An overlay lets the body button fill the whole chip, so every pixel that
       is not a control opens the card.
       The handlers only arrive on a hovering pointer (see usePointerFine at the host), which is
       what makes hover-reveal honest here: on touch these do not exist at all and copy/cut live
       in the open card instead. */
    if (onCopy || onCut) {
      return (
        <div className={clsx(chipBase, 'group flex')} style={style}>
          <button
            onClick={onClick}
            className="flex-1 min-w-0 px-1.5 py-1 flex items-center text-left"
            title={training.title}
          >
            {content}
          </button>
          <div
            data-admin-action
            className="absolute top-1 right-1 flex gap-1 z-20 opacity-100 transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 [@media(hover:hover)]:group-focus-within:opacity-100"
          >
            {onCopy && (
              <button
                onClick={(e) => { e.stopPropagation(); onCopy() }}
                className="shrink-0 p-1 rounded border border-surface-600 bg-surface-950/90 text-surface-200 shadow-sm hover:text-primary-300 hover:border-primary-400 transition-colors"
                title={t('clipboard.copy')}
                aria-label={t('clipboard.copy')}
              >
                <Copy className="w-3.5 h-3.5" />
              </button>
            )}
            {onCut && (
              <button
                onClick={(e) => { e.stopPropagation(); onCut() }}
                className="shrink-0 p-1 rounded border border-surface-600 bg-surface-950/90 text-surface-200 shadow-sm hover:text-amber-300 hover:border-amber-400 transition-colors"
                title={t('clipboard.cut')}
                aria-label={t('clipboard.cut')}
              >
                <Scissors className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      )
    }

    return renderBlock(
      { className: clsx(chipBase, 'px-1.5 py-1 flex items-center'), title: training.title, onClick, style },
      content,
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
  density?: BlockDensity
  // Coach view hides the "rate" CTA (only the athlete rates)
  isCoachView?: boolean
}

interface InvitationBlockProps {
  invitation: InvitationOverlayItem
  label: string
  onClick: () => void
  style?: React.CSSProperties
  density?: BlockDensity
}

// Held seat the athlete has NOT booked yet: amber call-to-action with a pulsing dot —
// deliberately nothing like the calm gray reservation, so it cannot pass for "already booked".
export function InvitationBlock({ invitation, label, onClick, style, density = 'full' }: InvitationBlockProps) {
  const title = invitation.title || label

  if (density === 'tile') {
    // Keeps its fill even at tile density: this is the one entry that needs an action from
    // the athlete, so it has to survive a glance across 42 cells.
    return renderBlock(
      {
        className: clsx(
          TILE_BASE,
          'bg-amber-500/15 border-l-amber-400 text-amber-300',
          'hover:bg-amber-500/25 transition-colors',
        ),
        title: `${label}: ${title}`,
        onClick,
      },
      <>
        <Star className="w-3 h-3 shrink-0" />
        <span className="flex-1 truncate font-semibold">{title}</span>
        {invitation.startTime && (
          <span className="shrink-0 text-[10px] tabular-nums opacity-80">
            {invitation.startTime.slice(0, 5)}
          </span>
        )}
        <span className="shrink-0 w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
      </>,
    )
  }

  // A control like every other entry, clipboard or not — the held seat it points at is the one
  // thing on this calendar that needs an action from the athlete, so it must always be openable.
  return renderBlock(
    {
      className: clsx(
        'border rounded-md text-left overflow-hidden',
        'transition-colors hover:bg-amber-500/35',
        'bg-amber-500/20 border-amber-500/70 text-amber-300',
        // Same height as the training chip: two kinds of entry sharing one lane must not
        // stack at two different heights.
        density === 'chip'
          ? 'relative w-full min-h-12 px-1.5 py-1 text-[11px] flex items-center'
          : 'absolute px-1.5 py-1 text-xs',
      ),
      title: `${label}: ${title}`,
      onClick,
      style,
    },
    <>
      <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
      <span className="flex items-center gap-1 min-w-0">
        <Star className="w-3 h-3 shrink-0" />
        <span className="font-semibold truncate">{density === 'chip' ? title : label}</span>
      </span>
      {density === 'full' && (
        <span className="block text-[10px] opacity-90 truncate">
          {invitation.startTime && invitation.endTime
            ? `${invitation.startTime.slice(0, 5)} - ${invitation.endTime.slice(0, 5)} · ${title}`
            : title}
        </span>
      )}
    </>,
  )
}

// Read-only overlay of a confirmed booking from the public reservation system.
export function ReservationBlock({ reservation, label, onClick, style, density = 'full', isCoachView }: ReservationBlockProps) {
  const { t } = useTranslation('training')
  const title = reservation.title || label
  // Rated → show the value; past & unrated & athlete → prompt to rate
  const rated = reservation.rpe != null
  const showRateCta = !isCoachView && !rated && reservation.canRate
  // One dot for two facts that mean the same thing to the person looking at it — something here
  // happened that you have not seen. `isNew` is the booking itself (coach side only);
  // `hasUnreadActivity` is a message in its thread, and both open the same modal.
  const unseen = reservation.isNew || reservation.hasUnreadActivity

  if (density === 'tile') {
    return renderBlock(
      {
        className: clsx(
          TILE_BASE,
          // Dashed left bar: a booking is not part of the plan the coach writes, and the
          // border style says so without spending a second colour.
          'border-dashed border-l-surface-500 bg-surface-800/40 text-surface-400',
          'hover:bg-surface-800/70 transition-colors',
        ),
        title,
        onClick,
      },
      <>
        <Lock className="w-3 h-3 shrink-0" />
        <span className="flex-1 truncate font-medium">{title}</span>
        <span className="shrink-0 text-[10px] tabular-nums">{reservation.startTime.slice(0, 5)}</span>
        {rated && (
          <span className="shrink-0 inline-flex items-center gap-px text-[10px]" aria-label={`RPE ${reservation.rpe}`}>
            <Gauge className="w-2.5 h-2.5" />{reservation.rpe}
          </span>
        )}
        {showRateCta && (
          <span className="shrink-0 inline-flex items-center gap-px text-[10px] text-amber-300">
            <Gauge className="w-2.5 h-2.5" />{t('rpe.rateShort')}
          </span>
        )}
        {unseen && <span className="shrink-0 w-2 h-2 rounded-full bg-rose-500" />}
      </>,
    )
  }

  return (
    <button
      onClick={onClick}
      style={style}
      className={clsx(
        'border border-dashed rounded-md text-left transition-colors overflow-hidden',
        'bg-surface-700/40 border-surface-500/60 text-surface-300 hover:bg-surface-700/60',
        density === 'chip' ? 'relative w-full px-1.5 py-0.5 text-[11px] truncate block' : 'absolute px-1.5 py-1 text-xs',
      )}
      title={title}
    >
      {unseen && (
        <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-rose-500" />
      )}
      <span className="flex items-center gap-1 min-w-0">
        <Lock className="w-3 h-3 shrink-0" />
        <span className="font-medium truncate">{title}</span>
      </span>
      {density === 'full' && (
        <span className="block text-[10px] opacity-80">
          {reservation.startTime.slice(0, 5)} - {reservation.endTime.slice(0, 5)}
        </span>
      )}
      {rated && (
        <span className="inline-flex items-center gap-0.5 mt-0.5 px-1 py-px rounded bg-surface-600/70 text-[9px] font-medium text-surface-200">
          <Gauge className="w-2.5 h-2.5" />RPE {reservation.rpe}
        </span>
      )}
      {showRateCta && (
        <span className="inline-flex items-center gap-0.5 mt-0.5 px-1 py-px rounded bg-amber-500/20 text-[9px] font-medium text-amber-300">
          <Gauge className="w-2.5 h-2.5" />{t('rpe.rateShort')}
        </span>
      )}
    </button>
  )
}
