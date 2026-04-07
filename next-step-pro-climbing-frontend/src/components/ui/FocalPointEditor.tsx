import { useRef, useState } from 'react'
import clsx from 'clsx'

interface FocalPoint {
  x: number
  y: number
}

interface FocalPointEditorProps {
  imageUrl: string
  value: FocalPoint
  onChange: (point: FocalPoint) => void
  aspectRatio?: string
  className?: string
}

export function FocalPointEditor({ imageUrl, value, onChange, aspectRatio = '1/1', className }: FocalPointEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const isDragging = useRef(false)
  const lastPos = useRef({ x: 0, y: 0 })
  const [naturalSize, setNaturalSize] = useState<{ w: number; h: number } | null>(null)

  const getOverflow = (): { x: number; y: number } => {
    if (!containerRef.current || !naturalSize) return { x: 1, y: 1 }
    const cW = containerRef.current.clientWidth
    const cH = containerRef.current.clientHeight
    const scale = Math.max(cW / naturalSize.w, cH / naturalSize.h)
    return {
      x: Math.max(1, naturalSize.w * scale - cW),
      y: Math.max(1, naturalSize.h * scale - cH),
    }
  }

  const applyDelta = (dx: number, dy: number) => {
    const overflow = getOverflow()
    onChange({
      x: Math.max(0, Math.min(1, value.x - dx / overflow.x)),
      y: Math.max(0, Math.min(1, value.y - dy / overflow.y)),
    })
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    isDragging.current = true
    lastPos.current = { x: e.clientX, y: e.clientY }
    e.preventDefault()
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging.current) return
    applyDelta(e.clientX - lastPos.current.x, e.clientY - lastPos.current.y)
    lastPos.current = { x: e.clientX, y: e.clientY }
  }

  const handleMouseUp = () => { isDragging.current = false }

  const handleTouchStart = (e: React.TouchEvent) => {
    isDragging.current = true
    lastPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
  }

  const handleTouchMove = (e: React.TouchEvent) => {
    if (!isDragging.current) return
    applyDelta(e.touches[0].clientX - lastPos.current.x, e.touches[0].clientY - lastPos.current.y)
    lastPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
    e.preventDefault()
  }

  const handleTouchEnd = () => { isDragging.current = false }

  return (
    <div
      ref={containerRef}
      className={clsx('relative overflow-hidden rounded-lg cursor-grab active:cursor-grabbing select-none ring-2 ring-primary-500/40', className)}
      style={{ aspectRatio }}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      <img
        src={imageUrl}
        alt=""
        className="w-full h-full object-cover pointer-events-none"
        style={{ objectPosition: `${value.x * 100}% ${value.y * 100}%` }}
        draggable={false}
        onLoad={(e) => {
          const img = e.currentTarget
          setNaturalSize({ w: img.naturalWidth, h: img.naturalHeight })
        }}
      />
      <div className="absolute bottom-2 right-2 bg-black/60 text-white text-xs px-2 py-1 rounded pointer-events-none">
        Przeciągnij aby ustawić kadr
      </div>
    </div>
  )
}
