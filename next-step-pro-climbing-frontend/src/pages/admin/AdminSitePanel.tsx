import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, Trash2, Library, ImageIcon, Save, X, Shield, MapPin, Plus, ArrowUp, ArrowDown } from 'lucide-react'
import { adminSiteApi } from '../../api/client'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { Modal } from '../../components/ui/Modal'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'
import { GalleryPickerModal } from '../../components/ui/GalleryPickerModal'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import type { AssetDto, LocationSectionDto, LocationContentDto, LocationPresetDto } from '../../types'

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
        <h2 className="text-xl font-semibold text-surface-100 mb-1">
          {t('site.title')}
        </h2>
        <p className="text-surface-400 text-sm">
          {t('site.subtitle')}
        </p>
      </div>

      {/* Hero image section */}
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-semibold text-surface-100">
            {t('site.heroTitle')}
          </h3>
          {hasPending && (
            <span className="text-xs px-2 py-1 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-full">
              {t('site.unsavedChanges')}
            </span>
          )}
        </div>

        <p className="text-surface-400 text-sm">
          {t('site.heroDescription')}
        </p>

        {/* Preview / FocalPointEditor */}
        {isLoading ? (
          <div className="w-full aspect-[21/9] rounded-lg bg-surface-900 border border-surface-700 flex items-center justify-center">
            <span className="text-surface-500 text-sm">{t('site.loading')}</span>
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
            <p className="text-xs text-surface-500">{t('site.focalPointHint')}</p>
          </div>
        ) : (
          <div className="w-full aspect-[21/9] rounded-lg bg-surface-900 border border-surface-700 flex flex-col items-center justify-center gap-2 text-surface-500">
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
              bg-surface-700 text-surface-100 hover:bg-surface-600
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

      {/* Badge section */}
      <BadgeSection />
      <BadgeLeftSection />

      {/* "Gdzie teraz szkolę" section */}
      <LocationSection />

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

const LOCATION_LANGS = [
  { code: 'pl', label: 'PL' },
  { code: 'en', label: 'EN' },
  { code: 'es', label: 'ES' },
] as const

const EMPTY_CONTENT: LocationContentDto = { badge: '', title: '', subtitle: '', locations: [] }

function LocationSection() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  // null = brak edycji (pokazujemy wartość z serwera); non-null = użytkownik edytuje
  const [edited, setEdited] = useState<LocationSectionDto | null>(null)
  const [activeLang, setActiveLang] = useState<string>('pl')
  const [saveError, setSaveError] = useState<string | null>(null)

  // Modal nazwy presetu: tryb create (nowy) lub rename (zmiana nazwy istniejącego)
  const [nameModal, setNameModal] = useState<{ mode: 'create' | 'rename'; presetId?: string } | null>(null)
  const [nameInput, setNameInput] = useState('')
  const [presetToApply, setPresetToApply] = useState<LocationPresetDto | null>(null)
  const [presetToDelete, setPresetToDelete] = useState<LocationPresetDto | null>(null)

  const { data: section, isLoading } = useQuery({
    queryKey: ['admin', 'homeLocation'],
    queryFn: adminSiteApi.getLocationSection,
  })

  const { data: presets } = useQuery({
    queryKey: ['admin', 'locationPresets'],
    queryFn: adminSiteApi.getLocationPresets,
  })

  const saveSectionMutation = useMutation({
    mutationFn: (s: LocationSectionDto) => adminSiteApi.saveLocationSection(s),
    onSuccess: (result) => {
      setSaveError(null)
      setEdited(null)
      queryClient.setQueryData(['admin', 'homeLocation'], result)
      queryClient.invalidateQueries({ queryKey: ['homeSettings'] })
    },
    onError: (e: Error) => setSaveError(e.message),
  })

  const savePresetMutation = useMutation({
    mutationFn: (preset: LocationPresetDto) => adminSiteApi.saveLocationPreset(preset),
    onSuccess: () => {
      setNameModal(null)
      setNameInput('')
      queryClient.invalidateQueries({ queryKey: ['admin', 'locationPresets'] })
    },
  })

  const deletePresetMutation = useMutation({
    mutationFn: (id: string) => adminSiteApi.deleteLocationPreset(id),
    onSuccess: () => {
      setPresetToDelete(null)
      queryClient.invalidateQueries({ queryKey: ['admin', 'locationPresets'] })
    },
  })

  const serverSection: LocationSectionDto | null = section
    ? { enabled: section.enabled, translations: section.translations }
    : null

  if (isLoading || serverSection === null) {
    return (
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <span className="text-surface-500 text-sm">{t('site.loading')}</span>
      </section>
    )
  }

  // Wyświetlana treść: edytowana (jeśli trwa edycja) lub wartość z serwera
  const draft = edited ?? serverSection
  const content = draft.translations[activeLang] ?? EMPTY_CONTENT
  const isDirty = edited !== null && JSON.stringify(edited) !== JSON.stringify(serverSection)
  const isBusy = saveSectionMutation.isPending

  const updateContent = (partial: Partial<LocationContentDto>) => {
    setEdited({
      ...draft,
      translations: { ...draft.translations, [activeLang]: { ...EMPTY_CONTENT, ...draft.translations[activeLang], ...partial } },
    })
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

  const openCreatePreset = () => { setNameModal({ mode: 'create' }); setNameInput('') }
  const openRenamePreset = (preset: LocationPresetDto) => {
    setNameModal({ mode: 'rename', presetId: preset.id ?? undefined })
    setNameInput(preset.name)
  }

  const submitName = () => {
    const name = nameInput.trim()
    if (!name) return
    if (nameModal?.mode === 'create') {
      // Czyścimy listy z pustych wpisów przed zapisem
      savePresetMutation.mutate({ id: null, name, translations: cleanTranslations(draft.translations) })
    } else if (nameModal?.mode === 'rename' && nameModal.presetId) {
      const preset = presets?.find(p => p.id === nameModal.presetId)
      if (preset) savePresetMutation.mutate({ ...preset, name })
    }
  }

  const applyPreset = (preset: LocationPresetDto) => {
    saveSectionMutation.mutate({ enabled: draft.enabled, translations: { ...preset.translations } })
    setPresetToApply(null)
  }

  return (
    <section className="bg-surface-800 border border-surface-700 rounded-lg p-6 space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MapPin className="w-5 h-5 text-amber-400" />
          <h3 className="text-base font-semibold text-surface-100">{t('site.location.title')}</h3>
        </div>
        {isDirty && (
          <span className="text-xs px-2 py-1 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-full">
            {t('site.unsavedChanges')}
          </span>
        )}
      </div>
      <p className="text-surface-400 text-sm">{t('site.location.description')}</p>

      {/* Toggle widoczności */}
      <label className="flex items-center gap-3 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={draft.enabled}
          onChange={e => setEdited({ ...draft, enabled: e.target.checked })}
          className="w-4 h-4 rounded border-surface-600 bg-surface-900 text-primary-500 focus:ring-primary-500"
        />
        <span className="text-sm text-surface-200">{t('site.location.enabledLabel')}</span>
      </label>

      {/* Zakładki języka */}
      <div className="flex gap-1.5">
        {LOCATION_LANGS.map(l => (
          <button
            key={l.code}
            type="button"
            onClick={() => setActiveLang(l.code)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              activeLang === l.code
                ? 'bg-primary-500 text-white'
                : 'bg-surface-900 text-surface-300 hover:bg-surface-700'
            }`}
          >
            {l.label}
          </button>
        ))}
      </div>

      {/* Pola tekstowe */}
      <div className="space-y-3">
        <Field label={t('site.location.badgeLabel')}>
          <input
            type="text"
            value={content.badge}
            onChange={e => updateContent({ badge: e.target.value })}
            className={inputClass}
          />
        </Field>
        <Field label={t('site.location.titleLabel')}>
          <input
            type="text"
            value={content.title}
            onChange={e => updateContent({ title: e.target.value })}
            className={inputClass}
          />
        </Field>
        <Field label={t('site.location.subtitleLabel')}>
          <textarea
            value={content.subtitle}
            onChange={e => updateContent({ subtitle: e.target.value })}
            rows={2}
            className={inputClass}
          />
        </Field>
      </div>

      {/* Lista miejsc */}
      <div className="space-y-2">
        <span className="block text-sm font-medium text-surface-300">{t('site.location.locationsLabel')}</span>
        {content.locations.map((place, idx) => (
          <div key={idx} className="flex items-center gap-2">
            <input
              type="text"
              value={place}
              onChange={e => updateLocation(idx, e.target.value)}
              placeholder={t('site.location.locationPlaceholder')}
              className={`${inputClass} flex-1`}
            />
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

      {/* Akcje zapisu */}
      <div className="flex flex-wrap gap-3 pt-2 border-t border-surface-700">
        <Button onClick={() => saveSectionMutation.mutate(draft)} disabled={isBusy || !isDirty}>
          <Save className="w-4 h-4 mr-2" />
          {saveSectionMutation.isPending ? t('site.saving') : t('site.location.saveActive')}
        </Button>
        <Button variant="secondary" onClick={openCreatePreset} disabled={isBusy}>
          <Library className="w-4 h-4 mr-2" />
          {t('site.location.saveAsPreset')}
        </Button>
      </div>

      {/* Presety */}
      <div className="space-y-2 pt-2">
        <h4 className="text-sm font-semibold text-surface-200">{t('site.location.presetsTitle')}</h4>
        <p className="text-xs text-surface-500">{t('site.location.presetsHint')}</p>
        {presets && presets.length > 0 ? (
          <ul className="space-y-2">
            {presets.map(preset => (
              <li key={preset.id ?? preset.name}
                className="flex items-center justify-between gap-2 bg-surface-900 border border-surface-700 rounded-lg px-3 py-2">
                <span className="text-sm text-surface-100 truncate">{preset.name}</span>
                <div className="flex gap-2 shrink-0">
                  <Button variant="secondary" onClick={() => setPresetToApply(preset)}>
                    {t('site.location.apply')}
                  </Button>
                  <Button variant="secondary" onClick={() => openRenamePreset(preset)}>
                    {t('site.location.rename')}
                  </Button>
                  <Button variant="danger" onClick={() => setPresetToDelete(preset)}>
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-surface-500">{t('site.location.noPresets')}</p>
        )}
      </div>

      {/* Modal nazwy presetu */}
      <Modal
        isOpen={nameModal !== null}
        onClose={() => setNameModal(null)}
        title={nameModal?.mode === 'rename' ? t('site.location.rename') : t('site.location.saveAsPreset')}
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
        isOpen={presetToApply !== null}
        title={t('site.location.applyConfirmTitle')}
        message={t('site.location.applyConfirmMessage')}
        confirmText={t('site.location.apply')}
        onConfirm={() => presetToApply && applyPreset(presetToApply)}
        onClose={() => setPresetToApply(null)}
      />

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

const inputClass =
  'w-full rounded-lg bg-surface-900 border border-surface-700 px-3 py-2 text-sm text-surface-100 placeholder-surface-500 focus:border-primary-500 focus:outline-none'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="block text-sm font-medium text-surface-300">{label}</span>
      {children}
    </label>
  )
}

/** Usuwa puste nazwy miejsc z każdego języka przed zapisem presetu. */
function cleanTranslations(
  translations: Record<string, LocationContentDto>,
): Record<string, LocationContentDto> {
  const out: Record<string, LocationContentDto> = {}
  for (const [lang, c] of Object.entries(translations)) {
    out[lang] = { ...c, locations: c.locations.filter(l => l.trim() !== '') }
  }
  return out
}
