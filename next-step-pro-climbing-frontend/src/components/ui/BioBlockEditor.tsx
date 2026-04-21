import { useRef, useState } from 'react'
import { ChevronUp, ChevronDown, X, Plus, Library, Images, HardDriveUpload } from 'lucide-react'
import { RichTextEditor } from './RichTextEditor'
import { MediaPickerModal } from './MediaPickerModal'
import { GalleryPickerModal } from './GalleryPickerModal'
import { adminAssetsApi } from '../../api/client'
import type { BioBlock } from './bioBlocks'

export type { BioBlock } from './bioBlocks'

// ─── Component ────────────────────────────────────────────────────────────────

type PickerAction = { action: 'add' } | { action: 'replace'; blockIdx: number }

export function BioBlockEditor({
  blocks,
  onChange,
}: {
  blocks: BioBlock[]
  onChange: (blocks: BioBlock[]) => void
}) {
  const [mediaPickerAction, setMediaPickerAction] = useState<PickerAction | null>(null)
  const [galleryPickerAction, setGalleryPickerAction] = useState<PickerAction | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const diskRef = useRef<HTMLInputElement>(null)
  const diskActionRef = useRef<PickerAction | null>(null)

  const moveUp = (idx: number) => {
    if (idx === 0) return
    const next = [...blocks]
    ;[next[idx - 1], next[idx]] = [next[idx], next[idx - 1]]
    onChange(next)
  }

  const moveDown = (idx: number) => {
    if (idx === blocks.length - 1) return
    const next = [...blocks]
    ;[next[idx], next[idx + 1]] = [next[idx + 1], next[idx]]
    onChange(next)
  }

  const remove = (idx: number) => onChange(blocks.filter((_, i) => i !== idx))

  const updateText = (idx: number, content: string) => {
    const next = [...blocks]
    next[idx] = { type: 'text', content }
    onChange(next)
  }

  const applyImageUrl = (url: string, action: PickerAction | null) => {
    if (!action) return
    if (action.action === 'add') {
      onChange([...blocks, { type: 'image', url, alt: '' }])
    } else {
      const next = [...blocks]
      next[action.blockIdx] = { type: 'image', url, alt: '' }
      onChange(next)
    }
  }

  const handleDiskChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    e.target.value = ''
    setIsUploading(true)
    try {
      const asset = await adminAssetsApi.upload(file)
      applyImageUrl(asset.url, diskActionRef.current)
    } finally {
      setIsUploading(false)
    }
  }

  const openDisk = (action: PickerAction) => {
    diskActionRef.current = action
    diskRef.current?.click()
  }

  const btnCls = 'p-1.5 rounded-lg bg-dark-900/80 text-dark-300 hover:text-dark-100 transition-colors'
  const arrowCls = 'p-1 rounded text-dark-500 hover:text-dark-100 hover:bg-dark-700 disabled:opacity-25 disabled:cursor-not-allowed transition-colors'

  return (
    <div className="space-y-3">
      {blocks.length === 0 && (
        <p className="text-sm text-dark-500 italic">
          Brak bloków. Dodaj tekst lub zdjęcie poniżej.
        </p>
      )}

      {blocks.map((block, idx) => (
        <div key={idx} className="flex gap-2 items-start">
          {/* Order arrows */}
          <div className="flex flex-col gap-0.5 shrink-0 pt-1">
            <button type="button" onClick={() => moveUp(idx)} disabled={idx === 0} className={arrowCls} title="W górę">
              <ChevronUp className="w-4 h-4" />
            </button>
            <button type="button" onClick={() => moveDown(idx)} disabled={idx === blocks.length - 1} className={arrowCls} title="W dół">
              <ChevronDown className="w-4 h-4" />
            </button>
          </div>

          {/* Content */}
          <div className="flex-1 min-w-0">
            {block.type === 'text' ? (
              <RichTextEditor
                value={block.content}
                onChange={(v) => updateText(idx, v)}
                rows={4}
              />
            ) : (
              <div className="relative rounded-lg overflow-hidden border border-dark-600 bg-dark-800">
                <img src={block.url} alt={block.alt} className="max-h-56 w-full object-contain" />
                {/* Replace buttons overlay */}
                <div className="absolute top-2 right-2 flex gap-1">
                  <button type="button" className={btnCls} title="Zamień (z biblioteki)"
                    onClick={() => setMediaPickerAction({ action: 'replace', blockIdx: idx })}>
                    <Library className="w-3.5 h-3.5" />
                  </button>
                  <button type="button" className={btnCls} title="Zamień (z galerii)"
                    onClick={() => setGalleryPickerAction({ action: 'replace', blockIdx: idx })}>
                    <Images className="w-3.5 h-3.5" />
                  </button>
                  <button type="button" className={btnCls} title="Zamień (z dysku)"
                    onClick={() => openDisk({ action: 'replace', blockIdx: idx })}>
                    <HardDriveUpload className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Delete */}
          <button type="button" onClick={() => remove(idx)}
            className="p-1.5 mt-1 shrink-0 rounded text-dark-500 hover:text-rose-400 transition-colors"
            title="Usuń blok">
            <X className="w-4 h-4" />
          </button>
        </div>
      ))}

      {/* Add row */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 pt-1 border-t border-dark-700">
        <button type="button"
          onClick={() => onChange([...blocks, { type: 'text', content: '' }])}
          className="flex items-center gap-1.5 text-sm text-primary-400 hover:text-primary-300 transition-colors">
          <Plus className="w-3.5 h-3.5" /> Dodaj tekst
        </button>

        <span className="text-dark-600 text-xs">|</span>
        <span className="text-xs text-dark-400">Dodaj zdjęcie:</span>

        <button type="button"
          onClick={() => setMediaPickerAction({ action: 'add' })}
          className="flex items-center gap-1 text-sm text-dark-300 hover:text-dark-100 transition-colors"
          title="Z biblioteki mediów">
          <Library className="w-3.5 h-3.5" /> Biblioteka
        </button>
        <button type="button"
          onClick={() => setGalleryPickerAction({ action: 'add' })}
          className="flex items-center gap-1 text-sm text-dark-300 hover:text-dark-100 transition-colors"
          title="Z galerii">
          <Images className="w-3.5 h-3.5" /> Galeria
        </button>
        <button type="button"
          onClick={() => openDisk({ action: 'add' })}
          disabled={isUploading}
          className="flex items-center gap-1 text-sm text-dark-300 hover:text-dark-100 disabled:opacity-50 transition-colors"
          title="Z dysku">
          <HardDriveUpload className="w-3.5 h-3.5" />
          {isUploading ? 'Wgrywanie…' : 'Dysk'}
        </button>
      </div>

      {/* Hidden disk input */}
      <input ref={diskRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleDiskChange} />

      {/* Media Library Picker */}
      <MediaPickerModal
        isOpen={mediaPickerAction !== null}
        onClose={() => setMediaPickerAction(null)}
        onSelect={(asset) => { applyImageUrl(asset.url, mediaPickerAction); setMediaPickerAction(null) }}
      />

      {/* Gallery Picker */}
      <GalleryPickerModal
        isOpen={galleryPickerAction !== null}
        onClose={() => setGalleryPickerAction(null)}
        onSelect={(url) => { applyImageUrl(url, galleryPickerAction); setGalleryPickerAction(null) }}
      />
    </div>
  )
}
