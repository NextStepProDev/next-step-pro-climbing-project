import { useRef, useState, useCallback, useEffect } from 'react'

const HOUR_HEIGHT = 40
const START_HOUR = 7
const END_HOUR = 23
const SNAP_MINUTES = 15
const MIN_DURATION_MINUTES = 15
const DRAG_THRESHOLD_PX = 5

function snapMinutes(minutes: number): number {
  return Math.round(minutes / SNAP_MINUTES) * SNAP_MINUTES
}

function minutesToTime(totalMinutes: number): string {
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

function timeToMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

interface GestureState {
  mode: 'move' | 'resize'
  slotId: string
  slotDate: string
  slotStartTime: string
  durationMinutes: number
  grabOffsetMinutes: number
  originalStartAbsMinutes: number
}

export interface GhostRect {
  left: number
  top: number
  width: number
  height: number
}

export interface LiveDragState {
  slotId: string
  mode: 'move' | 'resize'
  dayIndex: number
  topPx: number
  heightPx: number
  ghost: GhostRect | null
}

interface UseSlotDragOptions {
  days: { date: string }[]
  dayColumnRefs: React.MutableRefObject<(HTMLDivElement | null)[]>
  onDrop: (slotId: string, newDate: string, newStartTime: string, newEndTime: string, oldDate: string, oldStartTime: string, oldEndTime: string) => void
  enabled: boolean
}

export function useSlotDrag({ days, dayColumnRefs, onDrop, enabled }: UseSlotDragOptions) {
  const gestureRef = useRef<GestureState | null>(null)
  const livePosRef = useRef<LiveDragState | null>(null)
  const hasMoved = useRef(false)
  const pointerDownPos = useRef({ x: 0, y: 0 })
  const lastDragSlotRef = useRef<string | null>(null)
  const [dragState, setDragState] = useState<LiveDragState | null>(null)

  // Keep stable refs updated via effects (avoids writing to refs during render)
  const daysRef = useRef(days)
  useEffect(() => { daysRef.current = days }, [days])

  const onDropRef = useRef(onDrop)
  useEffect(() => { onDropRef.current = onDrop }, [onDrop])

  const buildGhostRect = useCallback((dayIndex: number, topPx: number, heightPx: number): GhostRect | null => {
    const col = dayColumnRefs.current[dayIndex]
    if (!col) return null
    const rect = col.getBoundingClientRect()
    return { left: rect.left, top: rect.top + topPx, width: rect.width, height: heightPx }
  }, [dayColumnRefs])

  const getDayIndex = useCallback((clientX: number): number => {
    let best = 0
    let bestDist = Infinity
    dayColumnRefs.current.forEach((el, i) => {
      if (!el) return
      const rect = el.getBoundingClientRect()
      if (clientX >= rect.left && clientX <= rect.right) {
        best = i
        bestDist = 0
      } else {
        const dist = Math.min(Math.abs(clientX - rect.left), Math.abs(clientX - rect.right))
        if (dist < bestDist) { bestDist = dist; best = i }
      }
    })
    return best
  }, [dayColumnRefs])

  const getColumnTop = useCallback((dayIndex: number): number => {
    return dayColumnRefs.current[dayIndex]?.getBoundingClientRect().top ?? 0
  }, [dayColumnRefs])

  // Document-level listeners — stable, read everything from refs
  useEffect(() => {
    if (!enabled) return

    const handleMove = (e: PointerEvent) => {
      const gesture = gestureRef.current
      if (!gesture) return

      const dx = Math.abs(e.clientX - pointerDownPos.current.x)
      const dy = Math.abs(e.clientY - pointerDownPos.current.y)
      if (!hasMoved.current && (dx > DRAG_THRESHOLD_PX || dy > DRAG_THRESHOLD_PX)) {
        hasMoved.current = true
      }
      if (!hasMoved.current) return

      let newPos: LiveDragState

      if (gesture.mode === 'move') {
        const dayIndex = getDayIndex(e.clientX)
        const columnTop = getColumnTop(dayIndex)
        const rawTopPx = e.clientY - columnTop - (gesture.grabOffsetMinutes / 60 * HOUR_HEIGHT)
        const rawMin = rawTopPx / HOUR_HEIGHT * 60
        const maxStartMin = (END_HOUR - START_HOUR) * 60 - gesture.durationMinutes
        const snapped = snapMinutes(Math.max(0, Math.min(rawMin, maxStartMin)))
        const topPx = snapped / 60 * HOUR_HEIGHT
        const heightPx = gesture.durationMinutes / 60 * HOUR_HEIGHT
        newPos = {
          slotId: gesture.slotId,
          mode: 'move',
          dayIndex,
          topPx,
          heightPx,
          ghost: buildGhostRect(dayIndex, topPx, heightPx),
        }
      } else {
        const dayIndex = getDayIndex(pointerDownPos.current.x)
        const columnTop = getColumnTop(dayIndex)
        const startTopPx = (gesture.originalStartAbsMinutes - START_HOUR * 60) / 60 * HOUR_HEIGHT
        const rawBottomPx = e.clientY - columnTop
        const rawEndMin = rawBottomPx / HOUR_HEIGHT * 60
        const minEndMin = (gesture.originalStartAbsMinutes - START_HOUR * 60) + MIN_DURATION_MINUTES
        const snapped = snapMinutes(Math.max(minEndMin, Math.min(rawEndMin, (END_HOUR - START_HOUR) * 60)))
        const heightPx = (snapped - (gesture.originalStartAbsMinutes - START_HOUR * 60)) / 60 * HOUR_HEIGHT
        newPos = {
          slotId: gesture.slotId,
          mode: 'resize',
          dayIndex,
          topPx: startTopPx,
          heightPx,
          ghost: buildGhostRect(dayIndex, startTopPx, heightPx),
        }
      }

      livePosRef.current = newPos
      setDragState(newPos)
    }

    const handleUp = () => {
      const gesture = gestureRef.current
      const livePos = livePosRef.current

      if (hasMoved.current && gesture && livePos) {
        lastDragSlotRef.current = gesture.slotId
        setTimeout(() => { lastDragSlotRef.current = null }, 100)

        const oldEndTime = minutesToTime(gesture.originalStartAbsMinutes + gesture.durationMinutes)
        if (gesture.mode === 'move') {
          const newDate = daysRef.current[livePos.dayIndex]?.date
          if (newDate) {
            const startAbsMin = START_HOUR * 60 + Math.round(livePos.topPx / HOUR_HEIGHT * 60)
            const snapped = snapMinutes(startAbsMin)
            onDropRef.current(
              gesture.slotId,
              newDate,
              minutesToTime(snapped),
              minutesToTime(snapped + gesture.durationMinutes),
              gesture.slotDate,
              gesture.slotStartTime,
              oldEndTime,
            )
          }
        } else {
          const startTopPx = (gesture.originalStartAbsMinutes - START_HOUR * 60) / 60 * HOUR_HEIGHT
          const endAbsMin = START_HOUR * 60 + Math.round((startTopPx + livePos.heightPx) / HOUR_HEIGHT * 60)
          const snapped = snapMinutes(endAbsMin)
          if (snapped > gesture.originalStartAbsMinutes) {
            onDropRef.current(
              gesture.slotId,
              gesture.slotDate,
              gesture.slotStartTime,
              minutesToTime(snapped),
              gesture.slotDate,
              gesture.slotStartTime,
              oldEndTime,
            )
          }
        }
      }

      gestureRef.current = null
      livePosRef.current = null
      hasMoved.current = false
      setDragState(null)
    }

    document.addEventListener('pointermove', handleMove)
    document.addEventListener('pointerup', handleUp)
    return () => {
      document.removeEventListener('pointermove', handleMove)
      document.removeEventListener('pointerup', handleUp)
    }
  }, [enabled, getDayIndex, getColumnTop, buildGhostRect])

  const onSlotPointerDown = useCallback((
    slotId: string,
    slotDate: string,
    slotStartTime: string,
    slotEndTime: string,
    e: React.PointerEvent<HTMLElement>,
  ) => {
    if (!enabled || e.button !== 0) return
    if ((e.target as HTMLElement).closest('[data-admin-action]')) return
    e.preventDefault()

    const dayIndex = getDayIndex(e.clientX)
    const columnTop = getColumnTop(dayIndex)
    const slotTopPx = (timeToMinutes(slotStartTime) - START_HOUR * 60) / 60 * HOUR_HEIGHT
    const grabOffsetMinutes = ((e.clientY - columnTop) - slotTopPx) / HOUR_HEIGHT * 60
    const durationMinutes = timeToMinutes(slotEndTime) - timeToMinutes(slotStartTime)

    pointerDownPos.current = { x: e.clientX, y: e.clientY }
    hasMoved.current = false

    gestureRef.current = {
      mode: 'move',
      slotId,
      slotDate,
      slotStartTime,
      durationMinutes,
      grabOffsetMinutes,
      originalStartAbsMinutes: timeToMinutes(slotStartTime),
    }

    const heightPx = durationMinutes / 60 * HOUR_HEIGHT
    const initial: LiveDragState = {
      slotId,
      mode: 'move',
      dayIndex,
      topPx: slotTopPx,
      heightPx,
      ghost: buildGhostRect(dayIndex, slotTopPx, heightPx),
    }
    livePosRef.current = initial
    setDragState(initial)
  }, [enabled, getDayIndex, getColumnTop, buildGhostRect])

  const onResizePointerDown = useCallback((
    slotId: string,
    slotDate: string,
    slotStartTime: string,
    slotEndTime: string,
    e: React.PointerEvent<HTMLElement>,
  ) => {
    if (!enabled || e.button !== 0) return
    e.preventDefault()
    e.stopPropagation()

    const dayIndex = getDayIndex(e.clientX)
    const slotTopPx = (timeToMinutes(slotStartTime) - START_HOUR * 60) / 60 * HOUR_HEIGHT
    const durationMinutes = timeToMinutes(slotEndTime) - timeToMinutes(slotStartTime)

    pointerDownPos.current = { x: e.clientX, y: e.clientY }
    hasMoved.current = false

    gestureRef.current = {
      mode: 'resize',
      slotId,
      slotDate,
      slotStartTime,
      durationMinutes,
      grabOffsetMinutes: 0,
      originalStartAbsMinutes: timeToMinutes(slotStartTime),
    }

    const heightPx = durationMinutes / 60 * HOUR_HEIGHT
    const initial: LiveDragState = {
      slotId,
      mode: 'resize',
      dayIndex,
      topPx: slotTopPx,
      heightPx,
      ghost: buildGhostRect(dayIndex, slotTopPx, heightPx),
    }
    livePosRef.current = initial
    setDragState(initial)
  }, [enabled, getDayIndex, buildGhostRect])

  const isBeingDragged = useCallback((slotId: string): boolean => {
    return dragState?.slotId === slotId && hasMoved.current
  }, [dragState])

  const wasJustDragged = useCallback((slotId: string): boolean => {
    return lastDragSlotRef.current === slotId
  }, [])

  return {
    dragState,
    isBeingDragged,
    wasJustDragged,
    onSlotPointerDown,
    onResizePointerDown,
  }
}
