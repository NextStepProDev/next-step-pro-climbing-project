import { useRef, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'

interface TimeScrollPickerProps {
  value: string // "HH:mm"
  onChange: (value: string) => void
  label?: string
}

const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = Array.from({ length: 12 }, (_, i) => i * 5) // 0, 5, 10, ..., 55

const ITEM_HEIGHT = 40

export function TimeScrollPicker({ value, onChange, label }: TimeScrollPickerProps) {
  const [h, m] = value.split(':').map(Number)
  const hour = h || 0
  // Clamped: a stored :58 rounded up to 60, which the column does not have — nothing showed as
  // selected and changing the hour then produced "11:60".
  const minute = Math.min(55, Math.round((m || 0) / 5) * 5)
  const { t } = useTranslation('common')

  const handleHourChange = useCallback(
    (newHour: number) => {
      onChange(`${String(newHour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`)
    },
    [minute, onChange]
  )

  const handleMinuteChange = useCallback(
    (newMinute: number) => {
      onChange(`${String(hour).padStart(2, '0')}:${String(newMinute).padStart(2, '0')}`)
    },
    [hour, onChange]
  )

  return (
    <div>
      {label && <label className="block text-sm text-surface-400 mb-1">{label}</label>}
      <div className="flex items-center gap-1 bg-surface-800 border border-surface-700 rounded-lg p-2">
        <ScrollColumn
          items={HOURS}
          selectedIndex={hour}
          name={label ? `${label}: ${t('timePicker.hour')}` : t('timePicker.hour')}
          onChange={handleHourChange}
          formatItem={(v) => String(v).padStart(2, '0')}
        />
        <span className="text-surface-300 text-xl font-bold px-1">:</span>
        <ScrollColumn
          items={MINUTES}
          selectedIndex={MINUTES.indexOf(minute)}
          name={label ? `${label}: ${t('timePicker.minute')}` : t('timePicker.minute')}
          onChange={(idx) => handleMinuteChange(MINUTES[idx])}
          formatItem={(v) => String(v).padStart(2, '0')}
        />
      </div>
    </div>
  )
}

function ScrollColumn({
  items,
  selectedIndex,
  name,
  onChange,
  formatItem,
}: {
  items: number[]
  selectedIndex: number
  name: string
  onChange: (index: number) => void
  formatItem: (value: number) => string
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const isScrollingRef = useRef(false)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  /* The column's position IS its value, so a scroll the component started itself is
   * indistinguishable from a user spin — including the half-finished frames of a smooth one.
   * A smooth scroll is interruptible (focus moving to another field cancels it), and the reader
   * below runs 80 ms after the last scroll event, so an interrupted animation leaves it reading
   * a row nobody chose and reporting it as a choice: reveal the pickers, click straight into the
   * date field, and the time silently becomes 09:00 instead of the 10:00 on screen. Rare — a
   * race, so it survived a long time — and invisible, because the form then agrees with itself.
   * Marking our own scrolls costs one ref and removes the whole class. */
  const selfScrollRef = useRef(false)

  // Scroll to selected item on mount and when selected changes externally
  useEffect(() => {
    const container = containerRef.current
    if (!container || isScrollingRef.current) return

    const targetScroll = selectedIndex * ITEM_HEIGHT
    if (Math.abs(container.scrollTop - targetScroll) < 1) return
    selfScrollRef.current = true
    container.scrollTo({ top: targetScroll, behavior: 'smooth' })
  }, [selectedIndex])

  const handleScroll = useCallback(() => {
    isScrollingRef.current = true

    if (scrollTimeoutRef.current) clearTimeout(scrollTimeoutRef.current)

    scrollTimeoutRef.current = setTimeout(() => {
      const container = containerRef.current
      if (!container) return

      const targetScroll = selectedIndex * ITEM_HEIGHT
      if (selfScrollRef.current) {
        // Our own scroll came back to us. Land it on the target — the animation may have been
        // cut short — and report nothing: the value never left the one the form already holds.
        selfScrollRef.current = false
        if (Math.abs(container.scrollTop - targetScroll) >= 1) {
          container.scrollTo({ top: targetScroll, behavior: 'auto' })
        }
        setTimeout(() => {
          isScrollingRef.current = false
        }, 100)
        return
      }

      const scrollTop = container.scrollTop
      const newIndex = Math.round(scrollTop / ITEM_HEIGHT)
      const clampedIndex = Math.max(0, Math.min(newIndex, items.length - 1))

      // Snap to position
      container.scrollTo({ top: clampedIndex * ITEM_HEIGHT, behavior: 'smooth' })

      if (clampedIndex !== selectedIndex) {
        onChange(clampedIndex)
      }

      setTimeout(() => {
        isScrollingRef.current = false
      }, 100)
    }, 80)
  }, [items.length, selectedIndex, onChange])

  return (
    <div className="relative w-14">
      {/* Highlight bar */}
      <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 h-10 bg-primary-500/15 rounded-md pointer-events-none z-10 border border-primary-500/30" />

      {/* Scroll container */}
      {/* A spinbutton: the columns answered clicks and the scroll wheel only, so without a mouse or a
          finger no time could be set at all. Up/Down step like any spinner (the scroll follows via
          the effect above), Home/End jump to the ends. */}
      <div
        ref={containerRef}
        onScroll={handleScroll}
        role="spinbutton"
        tabIndex={0}
        aria-label={name}
        aria-valuenow={items[selectedIndex]}
        aria-valuetext={formatItem(items[selectedIndex] ?? 0)}
        aria-valuemin={items[0]}
        aria-valuemax={items[items.length - 1]}
        onKeyDown={(e) => {
          const next =
            e.key === 'ArrowUp' ? selectedIndex + 1
            : e.key === 'ArrowDown' ? selectedIndex - 1
            : e.key === 'Home' ? 0
            : e.key === 'End' ? items.length - 1
            : null
          if (next === null) return
          e.preventDefault()
          const clamped = Math.max(0, Math.min(items.length - 1, next))
          if (clamped !== selectedIndex) onChange(clamped)
        }}
        className="overflow-y-auto scrollbar-hide relative rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        style={{
          height: ITEM_HEIGHT * 3,
          scrollSnapType: 'y mandatory',
        }}
      >
        {/* Top padding */}
        <div style={{ height: ITEM_HEIGHT }} />

        {items.map((item, idx) => (
          <div
            key={item}
            onClick={() => {
              onChange(idx)
              containerRef.current?.scrollTo({
                top: idx * ITEM_HEIGHT,
                behavior: 'smooth',
              })
            }}
            className={`flex items-center justify-center cursor-pointer transition-all ${
              idx === selectedIndex
                ? 'text-surface-100 font-bold text-lg'
                : 'text-surface-500 text-base'
            }`}
            style={{
              height: ITEM_HEIGHT,
              scrollSnapAlign: 'center',
            }}
          >
            {formatItem(item)}
          </div>
        ))}

        {/* Bottom padding */}
        <div style={{ height: ITEM_HEIGHT }} />
      </div>
    </div>
  )
}
