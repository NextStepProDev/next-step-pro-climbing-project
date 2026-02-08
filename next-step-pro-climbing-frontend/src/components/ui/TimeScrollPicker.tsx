import { useRef, useEffect, useCallback } from 'react'

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
  const minute = Math.round((m || 0) / 5) * 5

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
      {label && <label className="block text-sm text-dark-400 mb-1">{label}</label>}
      <div className="flex items-center gap-1 bg-dark-800 border border-dark-700 rounded-lg p-2">
        <ScrollColumn
          items={HOURS}
          selectedIndex={hour}
          onChange={handleHourChange}
          formatItem={(v) => String(v).padStart(2, '0')}
        />
        <span className="text-dark-300 text-xl font-bold px-1">:</span>
        <ScrollColumn
          items={MINUTES}
          selectedIndex={MINUTES.indexOf(minute)}
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
  onChange,
  formatItem,
}: {
  items: number[]
  selectedIndex: number
  onChange: (index: number) => void
  formatItem: (value: number) => string
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const isScrollingRef = useRef(false)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout>>()

  // Scroll to selected item on mount and when selected changes externally
  useEffect(() => {
    const container = containerRef.current
    if (!container || isScrollingRef.current) return

    const targetScroll = selectedIndex * ITEM_HEIGHT
    container.scrollTo({ top: targetScroll, behavior: 'smooth' })
  }, [selectedIndex])

  const handleScroll = useCallback(() => {
    isScrollingRef.current = true

    if (scrollTimeoutRef.current) clearTimeout(scrollTimeoutRef.current)

    scrollTimeoutRef.current = setTimeout(() => {
      const container = containerRef.current
      if (!container) return

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
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="overflow-y-auto scrollbar-hide relative"
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
                ? 'text-dark-100 font-bold text-lg'
                : 'text-dark-500 text-base'
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
