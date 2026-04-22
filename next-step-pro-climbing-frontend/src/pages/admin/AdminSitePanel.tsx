import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, Trash2, Library, ImageIcon, Save, X } from 'lucide-react'
import { adminSiteApi } from '../../api/client'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'
import { GalleryPickerModal } from '../../components/ui/GalleryPickerModal'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import type { AssetDto } from '../../types'

interface FocalPoint { x: number; y: number }

const DEFAULT_FOCAL: FocalPoint = { x: 0.5, y: 0.5 }

export function AdminSitePanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [showGalleryPicker, setShowGalleryPicker] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  // Staged URL/file — not saved to backend yet
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [pendingUrl, setPendingUrl] = useState<string | null>(null)
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string | null>(null)

  // Focal point override: null = use server value, non-null = user is editing
  // This avoids setState-in-effect by deriving the displayed value directly.
  const [focalOverride, setFocalOverride] = useState<FocalPoint | null>(null)

  const [saveError, setSaveError] = useState<string | null>(null)

  const { data: hero, isLoading } = useQuery({
    queryKey: ['admin', 'hero'],
    queryFn: adminSiteApi.getHero,
  })

  // Cleanup object URL when localPreviewUrl changes or on unmount
  useEffect(() => {
    return () => {
      if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    }
  }, [localPreviewUrl])

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'hero'] })
    queryClient.invalidateQueries({ queryKey: ['heroImage'] })
  }

  const clearPending = () => {
    if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    setPendingFile(null)
    setPendingUrl(null)
    setLocalPreviewUrl(null)
  }

  // Derived focal point: override wins; falls back to server value
  const focalPoint: FocalPoint = focalOverride ?? {
    x: hero?.focalPointX ?? 0.5,
    y: hero?.focalPointY ?? 0.5,
  }

  const hasPending = pendingFile !== null || pendingUrl !== null

  const isFocalPointDirty = !hasPending
    && focalOverride !== null
    && (focalOverride.x !== (hero?.focalPointX ?? 0.5)
      || focalOverride.y !== (hero?.focalPointY ?? 0.5))

  const canSave = hasPending || isFocalPointDirty

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (pendingFile) {
        return adminSiteApi.uploadHeroImage(pendingFile, focalPoint.x, focalPoint.y)
      } else if (pendingUrl) {
        return adminSiteApi.setHeroImageUrl(pendingUrl, focalPoint.x, focalPoint.y)
      } else {
        return adminSiteApi.setFocalPoint(focalPoint.x, focalPoint.y)
      }
    },
    onSuccess: () => {
      setSaveError(null)
      clearPending()
      setFocalOverride(null)
      invalidate()
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: adminSiteApi.deleteHeroImage,
    onSuccess: () => {
      setShowDeleteConfirm(false)
      clearPending()
      setFocalOverride(null)
      invalidate()
    },
  })

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    const objectUrl = URL.createObjectURL(file)
    setPendingFile(file)
    setPendingUrl(null)
    setLocalPreviewUrl(objectUrl)
    setFocalOverride(DEFAULT_FOCAL)
    e.target.value = ''
  }

  const handleAssetSelect = (asset: AssetDto) => {
    setShowMediaPicker(false)
    if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    setPendingFile(null)
    setPendingUrl(asset.url)
    setLocalPreviewUrl(null)
    setFocalOverride(DEFAULT_FOCAL)
  }

  const handleGallerySelect = (photoUrl: string) => {
    if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    setPendingFile(null)
    setPendingUrl(photoUrl)
    setLocalPreviewUrl(null)
    setFocalOverride(DEFAULT_FOCAL)
  }

  const handleCancel = () => {
    clearPending()
    setSaveError(null)
    setFocalOverride(null)
  }

  const savedImageUrl = hero?.imageUrl ?? null
  const displayUrl = localPreviewUrl ?? pendingUrl ?? savedImageUrl
  const isBusy = saveMutation.isPending || deleteMutation.isPending

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-xl font-semibold text-dark-100 mb-1">
          {t('site.title')}
        </h2>
        <p className="text-dark-400 text-sm">
          {t('site.subtitle')}
        </p>
      </div>

      {/* Hero image section */}
      <section className="bg-dark-800 border border-dark-700 rounded-lg p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-semibold text-dark-100">
            {t('site.heroTitle')}
          </h3>
          {hasPending && (
            <span className="text-xs px-2 py-1 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-full">
              {t('site.unsavedChanges')}
            </span>
          )}
        </div>

        <p className="text-dark-400 text-sm">
          {t('site.heroDescription')}
        </p>

        {/* Preview / FocalPointEditor */}
        {isLoading ? (
          <div className="w-full aspect-[21/9] rounded-lg bg-dark-900 border border-dark-700 flex items-center justify-center">
            <span className="text-dark-500 text-sm">{t('site.loading')}</span>
          </div>
        ) : displayUrl ? (
          <div className="space-y-2">
            <FocalPointEditor
              imageUrl={displayUrl}
              value={focalPoint}
              onChange={setFocalOverride}
              aspectRatio="21/9"
              className="w-full"
            />
            <p className="text-xs text-dark-500">{t('site.focalPointHint')}</p>
          </div>
        ) : (
          <div className="w-full aspect-[21/9] rounded-lg bg-dark-900 border border-dark-700 flex flex-col items-center justify-center gap-2 text-dark-500">
            <ImageIcon className="w-10 h-10" />
            <span className="text-sm">{t('site.noHeroImage')}</span>
          </div>
        )}

        {/* Error */}
        {saveError && (
          <p className="text-red-400 text-sm">{saveError}</p>
        )}

        {/* Save / Cancel row */}
        {canSave && (
          <div className="flex gap-3">
            <Button onClick={() => saveMutation.mutate()} disabled={isBusy}>
              <Save className="w-4 h-4 mr-2" />
              {saveMutation.isPending ? t('site.saving') : t('site.save')}
            </Button>
            <Button variant="secondary" onClick={handleCancel} disabled={isBusy}>
              <X className="w-4 h-4 mr-2" />
              {t('site.cancel')}
            </Button>
          </div>
        )}

        {/* Image source buttons */}
        <div className="flex flex-wrap gap-3">
          <label
            htmlFor="hero-file-input"
            className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors
              bg-dark-700 text-dark-100 hover:bg-dark-600
              ${isBusy ? 'opacity-50 cursor-not-allowed pointer-events-none' : 'cursor-pointer'}`}
          >
            <Upload className="w-4 h-4" />
            {t('site.uploadFromDisk')}
          </label>
          <input
            id="hero-file-input"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            disabled={isBusy}
            onChange={handleFileChange}
          />

          <Button
            variant="secondary"
            onClick={() => setShowMediaPicker(true)}
            disabled={isBusy}
          >
            <Library className="w-4 h-4 mr-2" />
            {t('site.pickFromLibrary')}
          </Button>

          <Button
            variant="secondary"
            onClick={() => setShowGalleryPicker(true)}
            disabled={isBusy}
          >
            <ImageIcon className="w-4 h-4 mr-2" />
            {t('site.pickFromGallery')}
          </Button>

          {savedImageUrl && !hasPending && (
            <Button
              variant="danger"
              onClick={() => setShowDeleteConfirm(true)}
              disabled={isBusy}
            >
              <Trash2 className="w-4 h-4 mr-2" />
              {t('site.deleteHeroImage')}
            </Button>
          )}
        </div>
      </section>

      {/* Modals */}
      <MediaPickerModal
        isOpen={showMediaPicker}
        onClose={() => setShowMediaPicker(false)}
        onSelect={handleAssetSelect}
      />

      <GalleryPickerModal
        isOpen={showGalleryPicker}
        onClose={() => setShowGalleryPicker(false)}
        onSelect={handleGallerySelect}
      />

      <ConfirmModal
        isOpen={showDeleteConfirm}
        title={t('site.deleteConfirmTitle')}
        message={t('site.deleteConfirmMessage')}
        confirmText={t('site.deleteHeroImage')}
        onConfirm={() => deleteMutation.mutate()}
        onClose={() => setShowDeleteConfirm(false)}
      />
    </div>
  )
}
