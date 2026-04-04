import { useRef } from 'react'

interface FocalPoint {
  x: number
  y: number
}

interface FocalPointEditorProps {
  imageUrl: string
  value: FocalPoint
  onChange: (point: FocalPoint) => void
}

export function FocalPointEditor({ imageUrl, value, onChange }: FocalPointEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const isDragging = useRef(false)

  const updateFromEvent = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!containerRef.current) return
    const rect = containerRef.current.getBoundingClientRect()
    const x = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    const y = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height))
    onChange({ x, y })
  }

  return (
    <div className="space-y-1">
      <div
        ref={containerRef}
        className="relative w-full aspect-video rounded-lg overflow-hidden cursor-crosshair select-none"
        onMouseDown={(e) => { isDragging.current = true; updateFromEvent(e) }}
        onMouseMove={(e) => { if (isDragging.current) updateFromEvent(e) }}
        onMouseUp={() => { isDragging.current = false }}
        onMouseLeave={() => { isDragging.current = false }}
      >
        <img
          src={imageUrl}
          alt=""
          className="w-full h-full object-cover pointer-events-none"
          draggable={false}
        />
        <div
          className="absolute top-0 bottom-0 w-px bg-white/70 pointer-events-none"
          style={{ left: `${value.x * 100}%` }}
        />
        <div
          className="absolute left-0 right-0 h-px bg-white/70 pointer-events-none"
          style={{ top: `${value.y * 100}%` }}
        />
        <div
          className="absolute w-5 h-5 -translate-x-1/2 -translate-y-1/2 pointer-events-none"
          style={{ left: `${value.x * 100}%`, top: `${value.y * 100}%` }}
        >
          <div className="w-full h-full rounded-full border-2 border-white bg-primary-500/70 shadow-md" />
        </div>
      </div>
      <p className="text-xs text-dark-500 text-right">
        {Math.round(value.x * 100)}% × {Math.round(value.y * 100)}%
      </p>
    </div>
  )
}
