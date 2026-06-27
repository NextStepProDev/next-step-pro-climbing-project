import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, Trash2, Library, ImageIcon, Save, X, Shield, MapPin, Plus, ArrowUp, ArrowDown, Pencil, Check, Megaphone } from 'lucide-react'
import { adminSiteApi } from '../../api/client'
import { useToast } from '../../context/ToastContext'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { Modal } from '../../components/ui/Modal'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'
import { GalleryPickerModal } from '../../components/ui/GalleryPickerModal'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import type { AssetDto, LocationContentDto, LocationPresetDto, CalendarPromoContentDto, CalendarPromoPresetDto } from '../../types'

interface FocalPoint { x: number; y: number }

const DEFAULT_FOCAL: FocalPoint = { x: 0.5, y: 0.5 }

export function AdminSitePanel() {
  const { t } = useTranslation('admin')

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-xl font-semibold text-surface-100 mb-1">
          {t('site.title')}
        </h2>
        <p className="text-surface-400 text-sm">
          {t('site.subtitle')}
        </p>
      </div>

      {/* Hero image — osobno desktop (panorama) i mobile (pionowe zdjęcie pod telefon) */}
      <HeroImageSection variant="desktop" />
      <HeroImageSection variant="mobile" />

      {/* Badge section */}
      <BadgeSection />
      <BadgeLeftSection />

      {/* "Gdzie teraz szkolę" section */}
      <LocationSection />

      {/* Promocja nad kalendarzem */}
      <CalendarPromoSection />
    </div>
  )
}

/**
 * Sekcja zarządzania zdjęciem hero — ta sama mechanika dla desktopu i mobile,
 * różnią się tylko endpointem, proporcjami kadru i opisami. Każda wersja ma
 * własny, niezależny stan i upload (osobne pliki na dysku).
 */
function HeroImageSection({ variant }: { variant: 'desktop' | 'mobile' }) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const isMobile = variant === 'mobile'

  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [showGalleryPicker, setShowGalleryPicker] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  // Staged URL/file — not saved to backend yet
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [pendingUrl, setPendingUrl] = useState<string | null>(null)
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string | null>(null)

  // Focal point override: null = use server value, non-null = user is editing
  const [focalOverride, setFocalOverride] = useState<FocalPoint | null>(null)

  const [saveError, setSaveError] = useState<string | null>(null)

  const api = isMobile
    ? {
        get: adminSiteApi.getHeroMobile,
        upload: adminSiteApi.uploadHeroMobileImage,
        setUrl: adminSiteApi.setHeroMobileImageUrl,
        setFocal: adminSiteApi.setHeroMobileFocalPoint,
        remove: adminSiteApi.deleteHeroMobileImage,
      }
    : {
        get: adminSiteApi.getHero,
        upload: adminSiteApi.uploadHeroImage,
        setUrl: adminSiteApi.setHeroImageUrl,
        setFocal: adminSiteApi.setFocalPoint,
        remove: adminSiteApi.deleteHeroImage,
      }

  const queryKeyName = isMobile ? 'heroMobile' : 'hero'
  const aspectRatio = isMobile ? '9/16' : '21/9'
  const fileInputId = isMobile ? 'hero-mobile-file-input' : 'hero-desktop-file-input'

  const { data: hero, isLoading } = useQuery({
    queryKey: ['admin', queryKeyName],
    queryFn: api.get,
  })

  // Cleanup object URL when localPreviewUrl changes or on unmount
  useEffect(() => {
    return () => {
      if (localPreviewUrl) URL.revokeObjectURL(localPreviewUrl)
    }
  }, [localPreviewUrl])

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', queryKeyName] })
    queryClient.invalidateQueries({ queryKey: ['heroImage'] })
    queryClient.invalidateQueries({ queryKey: ['homeSettings'] })
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
        return api.upload(pendingFile, focalPoint.x, focalPoint.y)
      } else if (pendingUrl) {
        return api.setUrl(pendingUrl, focalPoint.x, focalPoint.y)
      } else {
        return api.setFocal(focalPoint.x, focalPoint.y)
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
    mutationFn: api.remove,
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

  // Pionowy podgląd jest węższy, żeby nie zajmował całej szerokości panelu.
  const previewWrapClass = isMobile ? 'max-w-[220px]' : 'w-full'

  return (
    <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-surface-100">
          {t(isMobile ? 'site.heroMobileTitle' : 'site.heroDesktopTitle')}
        </h3>
        {hasPending && (
          <span className="text-xs px-2 py-1 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-full">
            {t('site.unsavedChanges')}
          </span>
        )}
      </div>

      <p className="text-surface-400 text-sm">
        {t(isMobile ? 'site.heroMobileDescription' : 'site.heroDesktopDescription')}
      </p>

      {/* Preview / FocalPointEditor */}
      {isLoading ? (
        <div className={`${previewWrapClass} rounded-lg bg-surface-900 border border-surface-700 flex items-center justify-center`} style={{ aspectRatio }}>
          <span className="text-surface-500 text-sm">{t('site.loading')}</span>
        </div>
      ) : displayUrl ? (
        <div className="space-y-2">
          <FocalPointEditor
            imageUrl={displayUrl}
            value={focalPoint}
            onChange={setFocalOverride}
            aspectRatio={aspectRatio}
            className={previewWrapClass}
          />
          <p className="text-xs text-surface-500">{t('site.focalPointHint')}</p>
        </div>
      ) : (
        <div className={`${previewWrapClass} rounded-lg bg-surface-900 border border-surface-700 flex flex-col items-center justify-center gap-2 text-surface-500`} style={{ aspectRatio }}>
          <ImageIcon className="w-10 h-10" />
          <span className="text-sm text-center px-3">{t(isMobile ? 'site.noHeroMobileImage' : 'site.noHeroImage')}</span>
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
          htmlFor={fileInputId}
          className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors
            bg-surface-700 text-surface-100 hover:bg-surface-600
            ${isBusy ? 'opacity-50 cursor-not-allowed pointer-events-none' : 'cursor-pointer'}`}
        >
          <Upload className="w-4 h-4" />
          {t('site.uploadFromDisk')}
        </label>
        <input
          id={fileInputId}
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
        message={t(isMobile ? 'site.deleteMobileConfirmMessage' : 'site.deleteConfirmMessage')}
        confirmText={t('site.deleteHeroImage')}
        onConfirm={() => deleteMutation.mutate()}
        onClose={() => setShowDeleteConfirm(false)}
      />
    </section>
  )
}

function BadgeSection() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [linkInput, setLinkInput] = useState<string | null>(null)
  const [linkDirty, setLinkDirty] = useState(false)

  const { data: badge, isLoading } = useQuery({
    queryKey: ['admin', 'badge'],
    queryFn: adminSiteApi.getBadge,
  })

  const displayLink = linkInput ?? badge?.linkUrl ?? ''

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'badge'] })
    queryClient.invalidateQueries({ queryKey: ['badgeImage'] })
    queryClient.invalidateQueries({ queryKey: ['homeSettings'] })
  }

  const setUrlMutation = useMutation({
    mutationFn: ({ url, linkUrl }: { url: string; linkUrl?: string }) =>
      adminSiteApi.setBadgeUrl(url, linkUrl),
    onSuccess: () => { setLinkInput(null); setLinkDirty(false); invalidate() },
  })

  const deleteMutation = useMutation({
    mutationFn: adminSiteApi.deleteBadge,
    onSuccess: () => {
      setShowDeleteConfirm(false)
      setLinkInput(null)
      setLinkDirty(false)
      invalidate()
    },
  })

  const handleAssetSelect = (asset: AssetDto) => {
    setShowMediaPicker(false)
    setUrlMutation.mutate({ url: asset.url, linkUrl: displayLink || undefined })
  }

  const handleLinkSave = () => {
    if (!badge?.imageUrl) return
    setUrlMutation.mutate({ url: badge.imageUrl, linkUrl: displayLink || undefined })
  }

  const savedUrl = badge?.imageUrl ?? null
  const isBusy = setUrlMutation.isPending || deleteMutation.isPending

  return (
    <>
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-4">
        <h3 className="text-base font-semibold text-surface-100">
          {t('site.badgeTitle')}
        </h3>
        <p className="text-surface-400 text-sm">
          {t('site.badgeDescription')}
        </p>

        {isLoading ? (
          <div className="w-24 h-24 rounded-lg bg-surface-900 border border-surface-700 flex items-center justify-center">
            <span className="text-surface-500 text-xs">{t('site.loading')}</span>
          </div>
        ) : savedUrl ? (
          <div className="inline-flex items-center gap-4 bg-surface-900 border border-surface-700 rounded-lg p-4">
            <img
              src={savedUrl}
              alt="Badge"
              className="w-20 h-20 object-contain rounded"
            />
          </div>
        ) : (
          <div className="w-24 h-24 rounded-lg bg-surface-900 border border-surface-700 flex flex-col items-center justify-center gap-1 text-surface-500">
            <Shield className="w-8 h-8" />
            <span className="text-xs">{t('site.noBadge')}</span>
          </div>
        )}

        {savedUrl && (
          <div className="space-y-2">
            <label className="block text-sm font-medium text-surface-300">
              {t('site.badgeLinkLabel')}
            </label>
            <div className="flex gap-2">
              <input
                type="url"
                value={displayLink}
                onChange={e => { setLinkInput(e.target.value); setLinkDirty(true) }}
                placeholder="https://..."
                className="flex-1 rounded-lg bg-surface-900 border border-surface-700 px-3 py-2 text-sm text-surface-100 placeholder-surface-500 focus:border-primary-500 focus:outline-none"
              />
              {linkDirty && (
                <Button onClick={handleLinkSave} disabled={isBusy}>
                  <Save className="w-4 h-4 mr-2" />
                  {t('site.save')}
                </Button>
              )}
            </div>
            <p className="text-xs text-surface-500">{t('site.badgeLinkHint')}</p>
          </div>
        )}

        <div className="flex flex-wrap gap-3">
          <Button
            variant="secondary"
            onClick={() => setShowMediaPicker(true)}
            disabled={isBusy}
          >
            <Library className="w-4 h-4 mr-2" />
            {t('site.pickBadgeFromLibrary')}
          </Button>

          {savedUrl && (
            <Button
              variant="danger"
              onClick={() => setShowDeleteConfirm(true)}
              disabled={isBusy}
            >
              <Trash2 className="w-4 h-4 mr-2" />
              {t('site.deleteBadge')}
            </Button>
          )}
        </div>
      </section>

      <MediaPickerModal
        isOpen={showMediaPicker}
        onClose={() => setShowMediaPicker(false)}
        onSelect={handleAssetSelect}
      />

      <ConfirmModal
        isOpen={showDeleteConfirm}
        title={t('site.deleteBadgeConfirmTitle')}
        message={t('site.deleteBadgeConfirmMessage')}
        confirmText={t('site.deleteBadge')}
        onConfirm={() => deleteMutation.mutate()}
        onClose={() => setShowDeleteConfirm(false)}
      />
    </>
  )
}

function BadgeLeftSection() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [linkInput, setLinkInput] = useState<string | null>(null)
  const [linkDirty, setLinkDirty] = useState(false)

  const { data: badge, isLoading } = useQuery({
    queryKey: ['admin', 'badgeLeft'],
    queryFn: adminSiteApi.getBadgeLeft,
  })

  const displayLink = linkInput ?? badge?.linkUrl ?? ''

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'badgeLeft'] })
    queryClient.invalidateQueries({ queryKey: ['badgeLeftImage'] })
    queryClient.invalidateQueries({ queryKey: ['homeSettings'] })
  }

  const setUrlMutation = useMutation({
    mutationFn: ({ url, linkUrl }: { url: string; linkUrl?: string }) =>
      adminSiteApi.setBadgeLeftUrl(url, linkUrl),
    onSuccess: () => { setLinkInput(null); setLinkDirty(false); invalidate() },
  })

  const deleteMutation = useMutation({
    mutationFn: adminSiteApi.deleteBadgeLeft,
    onSuccess: () => {
      setShowDeleteConfirm(false)
      setLinkInput(null)
      setLinkDirty(false)
      invalidate()
    },
  })

  const handleAssetSelect = (asset: AssetDto) => {
    setShowMediaPicker(false)
    setUrlMutation.mutate({ url: asset.url, linkUrl: displayLink || undefined })
  }

  const handleLinkSave = () => {
    if (!badge?.imageUrl) return
    setUrlMutation.mutate({ url: badge.imageUrl, linkUrl: displayLink || undefined })
  }

  const savedUrl = badge?.imageUrl ?? null
  const isBusy = setUrlMutation.isPending || deleteMutation.isPending

  return (
    <>
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-4">
        <h3 className="text-base font-semibold text-surface-100">
          {t('site.badgeLeftTitle')}
        </h3>
        <p className="text-surface-400 text-sm">
          {t('site.badgeLeftDescription')}
        </p>

        {isLoading ? (
          <div className="w-24 h-24 rounded-lg bg-surface-900 border border-surface-700 flex items-center justify-center">
            <span className="text-surface-500 text-xs">{t('site.loading')}</span>
          </div>
        ) : savedUrl ? (
          <div className="inline-flex items-center gap-4 bg-surface-900 border border-surface-700 rounded-lg p-4">
            <img
              src={savedUrl}
              alt="Badge"
              className="w-20 h-20 object-contain rounded"
            />
          </div>
        ) : (
          <div className="w-24 h-24 rounded-lg bg-surface-900 border border-surface-700 flex flex-col items-center justify-center gap-1 text-surface-500">
            <Shield className="w-8 h-8" />
            <span className="text-xs">{t('site.noBadgeLeft')}</span>
          </div>
        )}

        {savedUrl && (
          <div className="space-y-2">
            <label className="block text-sm font-medium text-surface-300">
              {t('site.badgeLinkLabel')}
            </label>
            <div className="flex gap-2">
              <input
                type="url"
                value={displayLink}
                onChange={e => { setLinkInput(e.target.value); setLinkDirty(true) }}
                placeholder="https://..."
                className="flex-1 rounded-lg bg-surface-900 border border-surface-700 px-3 py-2 text-sm text-surface-100 placeholder-surface-500 focus:border-primary-500 focus:outline-none"
              />
              {linkDirty && (
                <Button onClick={handleLinkSave} disabled={isBusy}>
                  <Save className="w-4 h-4 mr-2" />
                  {t('site.save')}
                </Button>
              )}
            </div>
            <p className="text-xs text-surface-500">{t('site.badgeLinkHint')}</p>
          </div>
        )}

        <div className="flex flex-wrap gap-3">
          <Button
            variant="secondary"
            onClick={() => setShowMediaPicker(true)}
            disabled={isBusy}
          >
            <Library className="w-4 h-4 mr-2" />
            {t('site.pickBadgeLeftFromLibrary')}
          </Button>

          {savedUrl && (
            <Button
              variant="danger"
              onClick={() => setShowDeleteConfirm(true)}
              disabled={isBusy}
            >
              <Trash2 className="w-4 h-4 mr-2" />
              {t('site.deleteBadgeLeft')}
            </Button>
          )}
        </div>
      </section>

      <MediaPickerModal
        isOpen={showMediaPicker}
        onClose={() => setShowMediaPicker(false)}
        onSelect={handleAssetSelect}
      />

      <ConfirmModal
        isOpen={showDeleteConfirm}
        title={t('site.deleteBadgeLeftConfirmTitle')}
        message={t('site.deleteBadgeLeftConfirmMessage')}
        confirmText={t('site.deleteBadgeLeft')}
        onConfirm={() => deleteMutation.mutate()}
        onClose={() => setShowDeleteConfirm(false)}
      />
    </>
  )
}

const inputClass =
  'w-full rounded-lg bg-surface-900 border border-surface-700 px-3 py-2 text-sm text-surface-100 placeholder-surface-500 focus:border-primary-500 focus:outline-none'

const LOCATION_LANGS = [
  { code: 'pl', label: 'PL' },
  { code: 'en', label: 'EN' },
  { code: 'es', label: 'ES' },
] as const

// Tytuł nie jest edytowalny (stały, z i18n) — edytujemy tylko badge, podtytuł i miejsca.
const EMPTY_CONTENT: LocationContentDto = { badge: '', subtitle: '', locations: [] }

function LocationSection() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  // Edytor szablonu: 'new' = nowy (pusty), 'edit' = istniejący; null = zamknięty (widok listy)
  const [editor, setEditor] = useState<{ mode: 'new' } | { mode: 'edit'; preset: LocationPresetDto } | null>(null)
  const [draft, setDraft] = useState<Record<string, LocationContentDto>>({})
  const [activeLang, setActiveLang] = useState<string>('pl')
  const [saveError, setSaveError] = useState<string | null>(null)

  // Modal nazwy: 'create' (nadanie nazwy nowemu szablonowi) lub 'rename' (zmiana nazwy istniejącego)
  const [nameModal, setNameModal] = useState<{ mode: 'create' } | { mode: 'rename'; preset: LocationPresetDto } | null>(null)
  const [nameInput, setNameInput] = useState('')
  const [presetToDelete, setPresetToDelete] = useState<LocationPresetDto | null>(null)

  const { data: presets, isLoading } = useQuery({
    queryKey: ['admin', 'locationPresets'],
    queryFn: adminSiteApi.getLocationPresets,
  })

  const { data: activeState } = useQuery({
    queryKey: ['admin', 'locationActive'],
    queryFn: adminSiteApi.getActiveState,
  })

  const activePresetId = activeState?.activePresetId ?? null

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'locationPresets'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'locationActive'] })
    queryClient.invalidateQueries({ queryKey: ['homeSettings'] })
  }

  const savePresetMutation = useMutation({
    mutationFn: (preset: LocationPresetDto) => adminSiteApi.saveLocationPreset(preset),
    onSuccess: () => {
      setSaveError(null)
      setEditor(null)
      setDraft({})
      setNameModal(null)
      setNameInput('')
      invalidateAll()
      showToast(t('site.location.presetSavedToast'))
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  const deletePresetMutation = useMutation({
    mutationFn: (id: string) => adminSiteApi.deleteLocationPreset(id),
    onSuccess: () => {
      setSaveError(null)
      setPresetToDelete(null)
      invalidateAll()
      showToast(t('site.location.presetDeletedToast'))
    },
    onError: (e: Error) => { setPresetToDelete(null); setSaveError(e.message) },
  })

  const setActiveMutation = useMutation({
    mutationFn: (presetId: string | null) => adminSiteApi.setActivePreset(presetId),
    onSuccess: (_res, presetId) => {
      setSaveError(null)
      queryClient.invalidateQueries({ queryKey: ['admin', 'locationActive'] })
      queryClient.invalidateQueries({ queryKey: ['homeSettings'] })
      showToast(presetId ? t('site.location.appliedToast') : t('site.location.disabledToast'))
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  if (isLoading) {
    return (
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <span className="text-surface-500 text-sm">{t('site.loading')}</span>
      </section>
    )
  }

  const content = draft[activeLang] ?? EMPTY_CONTENT

  const updateContent = (partial: Partial<LocationContentDto>) => {
    setDraft({ ...draft, [activeLang]: { ...EMPTY_CONTENT, ...draft[activeLang], ...partial } })
  }
  const updateLocation = (idx: number, value: string) => {
    const next = [...content.locations]
    next[idx] = value
    updateContent({ locations: next })
  }
  const addLocation = () => updateContent({ locations: [...content.locations, ''] })
  const removeLocation = (idx: number) =>
    updateContent({ locations: content.locations.filter((_, i) => i !== idx) })
  const moveLocation = (idx: number, dir: -1 | 1) => {
    const target = idx + dir
    if (target < 0 || target >= content.locations.length) return
    const next = [...content.locations]
    ;[next[idx], next[target]] = [next[target], next[idx]]
    updateContent({ locations: next })
  }

  const openNew = () => { setEditor({ mode: 'new' }); setDraft({}); setActiveLang('pl'); setSaveError(null) }
  const openEdit = (preset: LocationPresetDto) => {
    setEditor({ mode: 'edit', preset }); setDraft({ ...preset.translations }); setActiveLang('pl'); setSaveError(null)
  }
  const closeEditor = () => { setEditor(null); setDraft({}); setSaveError(null) }

  const saveEditor = () => {
    if (!editor) return
    if (editor.mode === 'edit') {
      savePresetMutation.mutate({ id: editor.preset.id, name: editor.preset.name, translations: cleanTranslations(draft) })
    } else {
      // Nowy szablon — najpierw poproś o nazwę.
      setNameModal({ mode: 'create' })
      setNameInput('')
    }
  }

  // Save stays disabled until the draft differs from the edited preset (edit),
  // or until some content has been entered (new template).
  const editorDirty = (() => {
    if (!editor) return false
    const cleaned = cleanTranslations(draft)
    if (editor.mode === 'edit') {
      return JSON.stringify(cleaned) !== JSON.stringify(cleanTranslations(editor.preset.translations))
    }
    return Object.values(cleaned).some(
      (c) => c.locations.length > 0 || c.badge.trim() !== '' || c.subtitle.trim() !== '',
    )
  })()

  const openRename = (preset: LocationPresetDto) => { setNameModal({ mode: 'rename', preset }); setNameInput(preset.name) }

  const submitName = () => {
    const name = nameInput.trim()
    if (!name || !nameModal) return
    if (nameModal.mode === 'create') {
      savePresetMutation.mutate({ id: null, name, translations: cleanTranslations(draft) })
    } else {
      savePresetMutation.mutate({ ...nameModal.preset, name })
    }
  }

  return (
    <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-5">
      <div className="flex items-center gap-2">
        <MapPin className="w-5 h-5 text-amber-400" />
        <h3 className="text-base font-semibold text-surface-100">{t('site.location.title')}</h3>
      </div>
      <p className="text-surface-400 text-sm">{t('site.location.description')}</p>

      {editor ? (
        <>
          {/* Baner edytora */}
          <div className="flex items-center gap-2 px-3 py-2 bg-primary-500/10 border border-primary-500/30 rounded-lg text-sm text-primary-300">
            <Pencil className="w-4 h-4 shrink-0" />
            {editor.mode === 'edit'
              ? t('site.location.editingTemplateBanner', { name: editor.preset.name })
              : t('site.location.newTemplateTitle')}
          </div>

          {/* Zakładki języka */}
          <div className="flex gap-1.5">
            {LOCATION_LANGS.map(l => (
              <button key={l.code} type="button" onClick={() => setActiveLang(l.code)}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  activeLang === l.code ? 'bg-primary-500 text-white' : 'bg-surface-900 text-surface-300 hover:bg-surface-700'
                }`}>
                {l.label}
              </button>
            ))}
          </div>
          {activeLang !== 'en' && (
            <p className="text-xs text-surface-500 -mt-2">{t('site.location.langFallbackHint')}</p>
          )}

          {/* Pola tekstowe — badge i podtytuł (tytuł jest stały, z i18n) */}
          <div className="space-y-3">
            <Field label={t('site.location.badgeLabel')}>
              <input type="text" value={content.badge}
                onChange={e => updateContent({ badge: e.target.value })} className={inputClass} />
            </Field>
            <Field label={t('site.location.subtitleLabel')}>
              <textarea value={content.subtitle} rows={2}
                onChange={e => updateContent({ subtitle: e.target.value })} className={inputClass} />
            </Field>
          </div>

          {/* Lista miejsc */}
          <div className="space-y-2">
            <span className="block text-sm font-medium text-surface-300">{t('site.location.locationsLabel')}</span>
            {content.locations.map((place, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <input type="text" value={place} onChange={e => updateLocation(idx, e.target.value)}
                  placeholder={t('site.location.locationPlaceholder')} className={`${inputClass} flex-1`} />
                <button type="button" onClick={() => moveLocation(idx, -1)} disabled={idx === 0}
                  className="p-2 text-surface-400 hover:text-surface-100 disabled:opacity-30" aria-label={t('site.location.moveUp')}>
                  <ArrowUp className="w-4 h-4" />
                </button>
                <button type="button" onClick={() => moveLocation(idx, 1)} disabled={idx === content.locations.length - 1}
                  className="p-2 text-surface-400 hover:text-surface-100 disabled:opacity-30" aria-label={t('site.location.moveDown')}>
                  <ArrowDown className="w-4 h-4" />
                </button>
                <button type="button" onClick={() => removeLocation(idx)}
                  className="p-2 text-red-400 hover:text-red-300" aria-label={t('site.location.remove')}>
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))}
            <Button variant="secondary" onClick={addLocation}>
              <Plus className="w-4 h-4 mr-2" />
              {t('site.location.addLocation')}
            </Button>
          </div>

          {saveError && <p className="text-red-400 text-sm">{saveError}</p>}

          <div className="flex flex-wrap gap-3 pt-2 border-t border-surface-700">
            <Button onClick={saveEditor} disabled={savePresetMutation.isPending || !editorDirty}>
              <Save className="w-4 h-4 mr-2" />
              {savePresetMutation.isPending ? t('site.saving') : t('site.location.saveTemplate')}
            </Button>
            <Button variant="secondary" onClick={closeEditor} disabled={savePresetMutation.isPending}>
              <X className="w-4 h-4 mr-2" />
              {t('site.cancel')}
            </Button>
          </div>
        </>
      ) : (
        <>
          <Button variant="secondary" onClick={openNew}>
            <Plus className="w-4 h-4 mr-2" />
            {t('site.location.addTemplate')}
          </Button>

          {saveError && <p className="text-red-400 text-sm">{saveError}</p>}

          <div className="space-y-2">
            <h4 className="text-sm font-semibold text-surface-200">{t('site.location.presetsTitle')}</h4>
            {presets && presets.length > 0 ? (
              <ul className="space-y-2">
                {presets.map(preset => {
                  const isActive = preset.id === activePresetId
                  return (
                    <li key={preset.id ?? preset.name}
                      className={`flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 bg-surface-900 border rounded-lg px-3 py-2 ${
                        isActive ? 'border-green-500/40' : 'border-surface-700'
                      }`}>
                      <span className="flex items-center gap-2 min-w-0">
                        <span className="text-sm text-surface-100 truncate">{preset.name}</span>
                        {isActive && (
                          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 bg-green-500/15 text-green-400 border border-green-500/30 rounded-full whitespace-nowrap shrink-0">
                            <Check className="w-3 h-3" />
                            {t('site.location.onSite')}
                          </span>
                        )}
                      </span>
                      <div className="flex flex-wrap gap-2 shrink-0">
                        {isActive ? (
                          <Button variant="secondary" onClick={() => setActiveMutation.mutate(null)} disabled={setActiveMutation.isPending}>
                            {t('site.location.disable')}
                          </Button>
                        ) : (
                          <Button variant="secondary" onClick={() => preset.id && setActiveMutation.mutate(preset.id)} disabled={setActiveMutation.isPending}>
                            {t('site.location.apply')}
                          </Button>
                        )}
                        <Button variant="secondary" onClick={() => openEdit(preset)}>
                          <Pencil className="w-4 h-4 mr-1" />
                          {t('site.location.edit')}
                        </Button>
                        <Button variant="secondary" onClick={() => openRename(preset)}>
                          {t('site.location.rename')}
                        </Button>
                        <Button variant="danger" onClick={() => setPresetToDelete(preset)}>
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </li>
                  )
                })}
              </ul>
            ) : (
              <p className="text-sm text-surface-500">{t('site.location.noPresets')}</p>
            )}
          </div>
        </>
      )}

      {/* Modal nazwy szablonu */}
      <Modal
        isOpen={nameModal !== null}
        onClose={() => setNameModal(null)}
        title={nameModal?.mode === 'rename' ? t('site.location.rename') : t('site.location.saveTemplate')}
      >
        <div className="space-y-4">
          <input
            type="text"
            autoFocus
            value={nameInput}
            onChange={e => setNameInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') submitName() }}
            placeholder={t('site.location.presetNamePlaceholder')}
            className={inputClass}
          />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setNameModal(null)}>{t('site.cancel')}</Button>
            <Button onClick={submitName} disabled={!nameInput.trim() || savePresetMutation.isPending}>
              {t('site.save')}
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmModal
        isOpen={presetToDelete !== null}
        title={t('site.location.deletePresetConfirmTitle')}
        message={t('site.location.deletePresetConfirmMessage')}
        confirmText={t('site.location.remove')}
        onConfirm={() => presetToDelete?.id && deletePresetMutation.mutate(presetToDelete.id)}
        onClose={() => setPresetToDelete(null)}
      />
    </section>
  )
}

const EMPTY_PROMO: CalendarPromoContentDto = { badge: '', title: '', description: '', ctaLabel: '', ctaUrl: '' }

function CalendarPromoSection() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [editor, setEditor] = useState<{ mode: 'new' } | { mode: 'edit'; preset: CalendarPromoPresetDto } | null>(null)
  const [draft, setDraft] = useState<Record<string, CalendarPromoContentDto>>({})
  const [activeLang, setActiveLang] = useState<string>('pl')
  const [saveError, setSaveError] = useState<string | null>(null)

  const [nameModal, setNameModal] = useState<{ mode: 'create' } | { mode: 'rename'; preset: CalendarPromoPresetDto } | null>(null)
  const [nameInput, setNameInput] = useState('')
  const [presetToDelete, setPresetToDelete] = useState<CalendarPromoPresetDto | null>(null)

  const { data: presets, isLoading } = useQuery({
    queryKey: ['admin', 'calendarPromoPresets'],
    queryFn: adminSiteApi.getCalendarPromoPresets,
  })

  const { data: activeState } = useQuery({
    queryKey: ['admin', 'calendarPromoActive'],
    queryFn: adminSiteApi.getCalendarPromoActiveState,
  })

  const activePresetId = activeState?.activePresetId ?? null

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'calendarPromoPresets'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'calendarPromoActive'] })
    queryClient.invalidateQueries({ queryKey: ['calendarPromo'] })
  }

  const savePresetMutation = useMutation({
    mutationFn: (preset: CalendarPromoPresetDto) => adminSiteApi.saveCalendarPromoPreset(preset),
    onSuccess: () => {
      setSaveError(null)
      setEditor(null)
      setDraft({})
      setNameModal(null)
      setNameInput('')
      invalidateAll()
      showToast(t('site.calendarPromo.presetSavedToast'))
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  const deletePresetMutation = useMutation({
    mutationFn: (id: string) => adminSiteApi.deleteCalendarPromoPreset(id),
    onSuccess: () => {
      setSaveError(null)
      setPresetToDelete(null)
      invalidateAll()
      showToast(t('site.calendarPromo.presetDeletedToast'))
    },
    onError: (e: Error) => { setPresetToDelete(null); setSaveError(e.message) },
  })

  const setActiveMutation = useMutation({
    mutationFn: (presetId: string | null) => adminSiteApi.setCalendarPromoActivePreset(presetId),
    onSuccess: (_res, presetId) => {
      setSaveError(null)
      queryClient.invalidateQueries({ queryKey: ['admin', 'calendarPromoActive'] })
      queryClient.invalidateQueries({ queryKey: ['calendarPromo'] })
      showToast(presetId ? t('site.calendarPromo.appliedToast') : t('site.calendarPromo.disabledToast'))
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  if (isLoading) {
    return (
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <span className="text-surface-500 text-sm">{t('site.loading')}</span>
      </section>
    )
  }

  const content = draft[activeLang] ?? EMPTY_PROMO

  const updateContent = (partial: Partial<CalendarPromoContentDto>) => {
    setDraft({ ...draft, [activeLang]: { ...EMPTY_PROMO, ...draft[activeLang], ...partial } })
  }

  const openNew = () => { setEditor({ mode: 'new' }); setDraft({}); setActiveLang('pl'); setSaveError(null) }
  const openEdit = (preset: CalendarPromoPresetDto) => {
    setEditor({ mode: 'edit', preset }); setDraft({ ...preset.translations }); setActiveLang('pl'); setSaveError(null)
  }
  const closeEditor = () => { setEditor(null); setDraft({}); setSaveError(null) }

  // Title + description są obowiązkowe — co najmniej jeden język musi mieć oba.
  const hasRequiredContent = (translations: Record<string, CalendarPromoContentDto>) =>
    Object.values(translations).some(c => c.title.trim() !== '' && c.description.trim() !== '')

  const saveEditor = () => {
    if (!editor) return
    if (!hasRequiredContent(draft)) {
      setSaveError(t('site.calendarPromo.requiredError'))
      return
    }
    if (editor.mode === 'edit') {
      savePresetMutation.mutate({ id: editor.preset.id, name: editor.preset.name, translations: cleanPromoTranslations(draft) })
    } else {
      setNameModal({ mode: 'create' })
      setNameInput('')
    }
  }

  // Save stays disabled until the draft differs from the edited preset (edit),
  // or until the required title+description are present (new template).
  const editorDirty = (() => {
    if (!editor) return false
    if (editor.mode === 'edit') {
      return JSON.stringify(cleanPromoTranslations(draft)) !== JSON.stringify(cleanPromoTranslations(editor.preset.translations))
    }
    return hasRequiredContent(draft)
  })()

  const openRename = (preset: CalendarPromoPresetDto) => { setNameModal({ mode: 'rename', preset }); setNameInput(preset.name) }

  const submitName = () => {
    const name = nameInput.trim()
    if (!name || !nameModal) return
    if (nameModal.mode === 'create') {
      savePresetMutation.mutate({ id: null, name, translations: cleanPromoTranslations(draft) })
    } else {
      savePresetMutation.mutate({ ...nameModal.preset, name })
    }
  }

  return (
    <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-5">
      <div className="flex items-center gap-2">
        <Megaphone className="w-5 h-5 text-amber-400" />
        <h3 className="text-base font-semibold text-surface-100">{t('site.calendarPromo.title')}</h3>
      </div>
      <p className="text-surface-400 text-sm">{t('site.calendarPromo.description')}</p>

      {editor ? (
        <>
          <div className="flex items-center gap-2 px-3 py-2 bg-primary-500/10 border border-primary-500/30 rounded-lg text-sm text-primary-300">
            <Pencil className="w-4 h-4 shrink-0" />
            {editor.mode === 'edit'
              ? t('site.calendarPromo.editingTemplateBanner', { name: editor.preset.name })
              : t('site.calendarPromo.newTemplateTitle')}
          </div>

          <div className="flex gap-1.5">
            {LOCATION_LANGS.map(l => (
              <button key={l.code} type="button" onClick={() => setActiveLang(l.code)}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  activeLang === l.code ? 'bg-primary-500 text-white' : 'bg-surface-900 text-surface-300 hover:bg-surface-700'
                }`}>
                {l.label}
              </button>
            ))}
          </div>
          {activeLang !== 'en' && (
            <p className="text-xs text-surface-500 -mt-2">{t('site.calendarPromo.langFallbackHint')}</p>
          )}

          <div className="space-y-3">
            <Field label={t('site.calendarPromo.badgeLabel')}>
              <input type="text" value={content.badge} placeholder={t('site.calendarPromo.badgePlaceholder')}
                onChange={e => updateContent({ badge: e.target.value })} className={inputClass} />
            </Field>
            <Field label={t('site.calendarPromo.titleLabel')}>
              <input type="text" value={content.title}
                onChange={e => updateContent({ title: e.target.value })} className={inputClass} />
            </Field>
            <Field label={t('site.calendarPromo.descriptionLabel')}>
              <textarea value={content.description} rows={2}
                onChange={e => updateContent({ description: e.target.value })} className={inputClass} />
            </Field>
            <div className="grid sm:grid-cols-2 gap-3">
              <Field label={t('site.calendarPromo.ctaLabelLabel')}>
                <input type="text" value={content.ctaLabel} placeholder={t('site.calendarPromo.ctaLabelPlaceholder')}
                  onChange={e => updateContent({ ctaLabel: e.target.value })} className={inputClass} />
              </Field>
              <Field label={t('site.calendarPromo.ctaUrlLabel')}>
                <input type="text" value={content.ctaUrl} placeholder={t('site.calendarPromo.ctaUrlPlaceholder')}
                  onChange={e => updateContent({ ctaUrl: e.target.value })} className={inputClass} />
              </Field>
            </div>
            <p className="text-xs text-surface-500">{t('site.calendarPromo.ctaHint')}</p>
          </div>

          {saveError && <p className="text-red-400 text-sm">{saveError}</p>}

          <div className="flex flex-wrap gap-3 pt-2 border-t border-surface-700">
            <Button onClick={saveEditor} disabled={savePresetMutation.isPending || !editorDirty}>
              <Save className="w-4 h-4 mr-2" />
              {savePresetMutation.isPending ? t('site.saving') : t('site.calendarPromo.saveTemplate')}
            </Button>
            <Button variant="secondary" onClick={closeEditor} disabled={savePresetMutation.isPending}>
              <X className="w-4 h-4 mr-2" />
              {t('site.cancel')}
            </Button>
          </div>
        </>
      ) : (
        <>
          <Button variant="secondary" onClick={openNew}>
            <Plus className="w-4 h-4 mr-2" />
            {t('site.calendarPromo.addTemplate')}
          </Button>

          {saveError && <p className="text-red-400 text-sm">{saveError}</p>}

          <div className="space-y-2">
            <h4 className="text-sm font-semibold text-surface-200">{t('site.calendarPromo.presetsTitle')}</h4>
            {presets && presets.length > 0 ? (
              <ul className="space-y-2">
                {presets.map(preset => {
                  const isActive = preset.id === activePresetId
                  return (
                    <li key={preset.id ?? preset.name}
                      className={`flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 bg-surface-900 border rounded-lg px-3 py-2 ${
                        isActive ? 'border-green-500/40' : 'border-surface-700'
                      }`}>
                      <span className="flex items-center gap-2 min-w-0">
                        <span className="text-sm text-surface-100 truncate">{preset.name}</span>
                        {isActive && (
                          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 bg-green-500/15 text-green-400 border border-green-500/30 rounded-full whitespace-nowrap shrink-0">
                            <Check className="w-3 h-3" />
                            {t('site.calendarPromo.onSite')}
                          </span>
                        )}
                      </span>
                      <div className="flex flex-wrap gap-2 shrink-0">
                        {isActive ? (
                          <Button variant="secondary" onClick={() => setActiveMutation.mutate(null)} disabled={setActiveMutation.isPending}>
                            {t('site.calendarPromo.disable')}
                          </Button>
                        ) : (
                          <Button variant="secondary" onClick={() => preset.id && setActiveMutation.mutate(preset.id)} disabled={setActiveMutation.isPending}>
                            {t('site.calendarPromo.apply')}
                          </Button>
                        )}
                        <Button variant="secondary" onClick={() => openEdit(preset)}>
                          <Pencil className="w-4 h-4 mr-1" />
                          {t('site.calendarPromo.edit')}
                        </Button>
                        <Button variant="secondary" onClick={() => openRename(preset)}>
                          {t('site.calendarPromo.rename')}
                        </Button>
                        <Button variant="danger" onClick={() => setPresetToDelete(preset)}>
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </li>
                  )
                })}
              </ul>
            ) : (
              <p className="text-sm text-surface-500">{t('site.calendarPromo.noPresets')}</p>
            )}
          </div>
        </>
      )}

      <Modal
        isOpen={nameModal !== null}
        onClose={() => setNameModal(null)}
        title={nameModal?.mode === 'rename' ? t('site.calendarPromo.rename') : t('site.calendarPromo.saveTemplate')}
      >
        <div className="space-y-4">
          <input
            type="text"
            autoFocus
            value={nameInput}
            onChange={e => setNameInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') submitName() }}
            placeholder={t('site.calendarPromo.presetNamePlaceholder')}
            className={inputClass}
          />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setNameModal(null)}>{t('site.cancel')}</Button>
            <Button onClick={submitName} disabled={!nameInput.trim() || savePresetMutation.isPending}>
              {t('site.save')}
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmModal
        isOpen={presetToDelete !== null}
        title={t('site.calendarPromo.deletePresetConfirmTitle')}
        message={t('site.calendarPromo.deletePresetConfirmMessage')}
        confirmText={t('site.calendarPromo.remove')}
        onConfirm={() => presetToDelete?.id && deletePresetMutation.mutate(presetToDelete.id)}
        onClose={() => setPresetToDelete(null)}
      />
    </section>
  )
}

/** Przycina białe znaki we wszystkich polach; pomija języki bez tytułu i opisu. */
function cleanPromoTranslations(
  translations: Record<string, CalendarPromoContentDto>,
): Record<string, CalendarPromoContentDto> {
  const out: Record<string, CalendarPromoContentDto> = {}
  for (const [lang, c] of Object.entries(translations)) {
    const cleaned: CalendarPromoContentDto = {
      badge: c.badge.trim(),
      title: c.title.trim(),
      description: c.description.trim(),
      ctaLabel: c.ctaLabel.trim(),
      ctaUrl: c.ctaUrl.trim(),
    }
    // Pomijaj puste języki (bez tytułu i opisu), by nie zaśmiecać szablonu.
    if (cleaned.title === '' && cleaned.description === '') continue
    out[lang] = cleaned
  }
  return out
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="block text-sm font-medium text-surface-300">{label}</span>
      {children}
    </label>
  )
}

/** Usuwa puste nazwy miejsc z każdego języka przed zapisem. */
function cleanTranslations(
  translations: Record<string, LocationContentDto>,
): Record<string, LocationContentDto> {
  const out: Record<string, LocationContentDto> = {}
  for (const [lang, c] of Object.entries(translations)) {
    out[lang] = { ...c, locations: c.locations.filter(l => l.trim() !== '') }
  }
  return out
}
