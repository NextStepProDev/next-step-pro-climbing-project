import { useState, useEffect, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { User, Upload, Trash2, Plus, Library, Images, X, ChevronUp, ChevronDown, Copy } from 'lucide-react'
import clsx from 'clsx'
import { adminInstructorApi } from '../../api/client'
import type { InstructorAdmin, InstructorType, CreateInstructorRequest, UpdateInstructorRequest } from '../../types'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { FileUpload } from '../../components/ui/FileUpload'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'
import { GalleryPickerModal } from '../../components/ui/GalleryPickerModal'
import { BioBlockEditor } from '../../components/ui/BioBlockEditor'
import { deserializeBio, serializeBio } from '../../components/ui/bioBlocks'
import type { BioBlock } from '../../components/ui/bioBlocks'
import { COURSE_CONTENT_LANGUAGES } from '../../constants/courseLanguages'

function CertificationsEditor({
  items,
  onChange,
  addLabel,
}: {
  items: string[]
  onChange: (items: string[]) => void
  addLabel: string
}) {
  const update = (idx: number, val: string) => {
    const next = [...items]; next[idx] = val; onChange(next)
  }
  const remove = (idx: number) => onChange(items.filter((_, i) => i !== idx))
  const add = () => onChange([...items, ''])

  return (
    <div className="space-y-2">
      {items.map((item, idx) => (
        <div key={idx} className="flex items-center gap-2">
          <span className="text-primary-400 text-base shrink-0 select-none">•</span>
          <input
            type="text"
            value={item}
            onChange={(e) => update(idx, e.target.value)}
            className="flex-1 px-3 py-1.5 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
          <button type="button" onClick={() => remove(idx)} className="p-1 text-surface-500 hover:text-rose-400 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>
      ))}
      <button type="button" onClick={add}
        className="flex items-center gap-1.5 text-sm text-primary-400 hover:text-primary-300 transition-colors mt-1">
        <Plus className="w-3.5 h-3.5" />{addLabel}
      </button>
    </div>
  )
}

function LanguageBadge({ language }: { language: string }) {
  return (
    <span
      className={clsx(
        'text-xs px-2 py-0.5 rounded-full font-medium',
        language === 'pl' && 'bg-blue-900/40 text-blue-400',
        language === 'en' && 'bg-green-900/40 text-green-400',
        language === 'es' && 'bg-purple-900/40 text-purple-400',
      )}
    >
      {language.toUpperCase()}
    </span>
  )
}

const LANG_ORDER = ['pl', 'en', 'es'] as const

interface Props { memberType: InstructorType }

export function AdminTeamMemberPanel({ memberType }: Props) {
  const { t } = useTranslation('admin')
  const isInstructor = memberType === 'INSTRUCTOR'
  const entityLabel = isInstructor ? 'Instruktora' : 'Zawodnika'
  const certLabel = isInstructor ? 'Certyfikaty' : 'Osiągnięcia'
  const certAddLabel = isInstructor ? 'Dodaj certyfikat' : 'Dodaj osiągnięcie'
  const headerTitle = isInstructor ? 'Zarządzanie Instruktorami' : 'Zarządzanie Zawodnikami'

  const queryClient = useQueryClient()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [uploadPhotoModalOpen, setUploadPhotoModalOpen] = useState(false)
  const [deleteGroupMembers, setDeleteGroupMembers] = useState<string[] | null>(null)
  const [selectedMember, setSelectedMember] = useState<InstructorAdmin | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [focalPoint, setFocalPoint] = useState({ x: 0.5, y: 0.5 })

  const [createBioBlocks, setCreateBioBlocks] = useState<BioBlock[]>([])
  const [editBioBlocks, setEditBioBlocks] = useState<BioBlock[]>([])
  const [createCerts, setCreateCerts] = useState<string[]>([])
  const [editCerts, setEditCerts] = useState<string[]>([])
  const [create8aUrl, setCreate8aUrl] = useState('')
  const [edit8aUrl, setEdit8aUrl] = useState('')

  const [badgePickerOpen, setBadgePickerOpen] = useState(false)
  const [badgeTargetId, setBadgeTargetId] = useState<string | null>(null)
  const [photoGalleryOpen, setPhotoGalleryOpen] = useState(false)
  const [deleteSingleConfirm, setDeleteSingleConfirm] = useState(false)
  const [showCreateExitConfirm, setShowCreateExitConfirm] = useState(false)
  const [showEditExitConfirm, setShowEditExitConfirm] = useState(false)
  const [showSyncMediaModal, setShowSyncMediaModal] = useState(false)
  const [pendingMediaAction, setPendingMediaAction] = useState<
    | { type: 'uploadPhoto'; id: string; file: File }
    | { type: 'deletePhoto'; id: string }
    | { type: 'setBadge'; id: string; badgeUrl: string }
    | { type: 'deleteBadge'; id: string }
    | { type: 'setPhotoUrl'; id: string; photoUrl: string | null }
    | null
  >(null)
  const editFormRef = useRef<HTMLFormElement>(null)

  const isCreateDirty = useCallback(() => {
    if (!createModalOpen) return false
    const form = document.querySelector<HTMLFormElement>('[data-form="create-member"]')
    if (!form) return false
    const firstName = (form.elements.namedItem('firstName') as HTMLInputElement)?.value ?? ''
    const lastName = (form.elements.namedItem('lastName') as HTMLInputElement)?.value ?? ''
    return firstName.trim() !== '' || lastName.trim() !== '' ||
      createBioBlocks.length > 0 || createCerts.some(c => c.trim() !== '') || create8aUrl.trim() !== ''
  }, [createModalOpen, createBioBlocks, createCerts, create8aUrl])

  const isEditDirty = useCallback(() => {
    if (!selectedMember || !editModalOpen) return false
    const form = editFormRef.current
    if (!form) return false
    const firstName = (form.elements.namedItem('firstName') as HTMLInputElement)?.value ?? ''
    const lastName = (form.elements.namedItem('lastName') as HTMLInputElement)?.value ?? ''
    const active = (form.elements.namedItem('active') as HTMLInputElement)?.checked ?? false
    const origBio = deserializeBio(selectedMember.bio ?? '')
    const origCerts = selectedMember.certifications ? selectedMember.certifications.split('\n').filter(Boolean) : []
    const bioChanged = serializeBio(editBioBlocks) !== serializeBio(origBio)
    const certsChanged = editCerts.filter(Boolean).join('\n') !== origCerts.join('\n')
    const focalChanged = selectedMember.photoUrl != null &&
      (focalPoint.x !== (selectedMember.focalPointX ?? 0.5) || focalPoint.y !== (selectedMember.focalPointY ?? 0.5))
    return firstName !== selectedMember.firstName ||
      lastName !== selectedMember.lastName ||
      active !== selectedMember.active ||
      bioChanged || certsChanged || focalChanged ||
      edit8aUrl.trim() !== (selectedMember.profile8aUrl ?? '')
  }, [selectedMember, editModalOpen, editBioBlocks, editCerts, edit8aUrl, focalPoint])

  const handleCreateClose = useCallback(() => {
    if (isCreateDirty()) {
      setShowCreateExitConfirm(true)
    } else {
      setCreateModalOpen(false)
    }
  }, [isCreateDirty])

  const handleEditClose = useCallback(() => {
    if (isEditDirty()) {
      setShowEditExitConfirm(true)
    } else {
      setEditModalOpen(false)
      setSelectedMember(null)
    }
  }, [isEditDirty])

  const handleEditSaveAndClose = useCallback(() => {
    editFormRef.current?.requestSubmit()
  }, [])

  const { data: allMembers, isLoading, error } = useQuery({
    queryKey: ['admin', 'instructors'],
    queryFn: adminInstructorApi.getAll,
  })

  const membersOfType = (allMembers ?? []).filter((m) => m.memberType === memberType)

  const groups = (() => {
    const map = new Map<string, InstructorAdmin[]>()
    for (const member of membersOfType) {
      const group = map.get(member.translationGroupId) ?? []
      group.push(member)
      map.set(member.translationGroupId, group)
    }
    return [...map.entries()]
      .sort((a, b) => {
        const orderA = Math.min(...a[1].map(m => m.displayOrder))
        const orderB = Math.min(...b[1].map(m => m.displayOrder))
        return orderA - orderB
      })
      .map(([groupId, groupMembers]) => ({ groupId, members: groupMembers }))
  })()

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
    queryClient.invalidateQueries({ queryKey: ['instructors'] })
  }

  const createMutation = useMutation({
    mutationFn: adminInstructorApi.create,
    onSuccess: () => {
      invalidateAll()
      setCreateModalOpen(false)
      setCreateBioBlocks([])
      setCreateCerts([])
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateInstructorRequest }) =>
      adminInstructorApi.update(id, data),
    onSuccess: () => {
      invalidateAll()
      setEditModalOpen(false)
      setSelectedMember(null)
    },
  })

  const deleteGroupMutation = useMutation({
    mutationFn: async (memberIds: string[]) => {
      for (const id of memberIds) {
        await adminInstructorApi.delete(id)
      }
    },
    onSuccess: () => {
      invalidateAll()
      setDeleteGroupMembers(null)
    },
  })

  const deleteSingleMutation = useMutation({
    mutationFn: adminInstructorApi.delete,
    onSuccess: () => {
      invalidateAll()
      setDeleteSingleConfirm(false)
      setEditModalOpen(false)
      setSelectedMember(null)
    },
  })

  const uploadPhotoMutation = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => adminInstructorApi.uploadPhoto(id, file),
    onSuccess: () => {
      invalidateAll()
      setUploadPhotoModalOpen(false)
      setSelectedMember(null)
      setPhotoPreview(null)
      setSelectedFile(null)
    },
  })

  const deletePhotoMutation = useMutation({
    mutationFn: adminInstructorApi.deletePhoto,
    onSuccess: () => {
      invalidateAll()
    },
  })

  const setBadgeMutation = useMutation({
    mutationFn: ({ id, badgeUrl }: { id: string; badgeUrl: string }) => adminInstructorApi.setBadge(id, badgeUrl),
    onSuccess: () => {
      invalidateAll()
    },
  })

  const deleteBadgeMutation = useMutation({
    mutationFn: adminInstructorApi.deleteBadge,
    onSuccess: () => {
      invalidateAll()
    },
  })

  const moveUpMutation = useMutation({
    mutationFn: adminInstructorApi.moveUp,
    onSuccess: (updated) => { queryClient.setQueryData(['admin', 'instructors'], updated) },
  })

  const moveDownMutation = useMutation({
    mutationFn: adminInstructorApi.moveDown,
    onSuccess: (updated) => { queryClient.setQueryData(['admin', 'instructors'], updated) },
  })

  const setPhotoUrlMutation = useMutation({
    mutationFn: ({ id, photoUrl }: { id: string; photoUrl: string | null }) =>
      adminInstructorApi.setPhotoUrl(id, photoUrl),
    onSuccess: () => {
      invalidateAll()
      setUploadPhotoModalOpen(false)
      setSelectedMember(null)
    },
  })

  const duplicateMutation = useMutation({
    mutationFn: ({ id, targetLanguage }: { id: string; targetLanguage: string }) =>
      adminInstructorApi.duplicateAsTranslation(id, targetLanguage),
    onSuccess: (newMember) => {
      invalidateAll()
      setEditModalOpen(false)
      setSelectedMember(null)
      setTimeout(() => {
        setSelectedMember(newMember)
        setFocalPoint({ x: newMember.focalPointX ?? 0.5, y: newMember.focalPointY ?? 0.5 })
        setEditBioBlocks(deserializeBio(newMember.bio ?? ''))
        setEditCerts(newMember.certifications ? newMember.certifications.split('\n').filter(Boolean) : [])
        setEdit8aUrl(newMember.profile8aUrl ?? '')
        setEditModalOpen(true)
      }, 100)
    },
  })

  const duplicateFromListMutation = useMutation({
    mutationFn: ({ id, targetLanguage }: { id: string; targetLanguage: string }) =>
      adminInstructorApi.duplicateAsTranslation(id, targetLanguage),
    onSuccess: (newMember) => {
      invalidateAll()
      setSelectedMember(newMember)
      setFocalPoint({ x: newMember.focalPointX ?? 0.5, y: newMember.focalPointY ?? 0.5 })
      setEditBioBlocks(deserializeBio(newMember.bio ?? ''))
      setEditCerts(newMember.certifications ? newMember.certifications.split('\n').filter(Boolean) : [])
      setEdit8aUrl(newMember.profile8aUrl ?? '')
      setEditModalOpen(true)
    },
  })

  const syncMediaMutation = useMutation({
    mutationFn: (id: string) => adminInstructorApi.syncMediaToTranslations(id),
    onSuccess: () => { invalidateAll() },
  })

  const hasSiblings = (memberId: string) => {
    const member = (allMembers ?? []).find(m => m.id === memberId)
    if (!member) return false
    return (allMembers ?? []).some(m => m.translationGroupId === member.translationGroupId && m.id !== memberId)
  }

  const executePendingAction = async (action: NonNullable<typeof pendingMediaAction>, sync: boolean) => {
    switch (action.type) {
      case 'uploadPhoto':
        await uploadPhotoMutation.mutateAsync({ id: action.id, file: action.file })
        break
      case 'deletePhoto':
        await deletePhotoMutation.mutateAsync(action.id)
        break
      case 'setBadge':
        await setBadgeMutation.mutateAsync({ id: action.id, badgeUrl: action.badgeUrl })
        break
      case 'deleteBadge':
        await deleteBadgeMutation.mutateAsync(action.id)
        break
      case 'setPhotoUrl':
        await setPhotoUrlMutation.mutateAsync({ id: action.id, photoUrl: action.photoUrl })
        break
    }
    if (sync) {
      await syncMediaMutation.mutateAsync(action.id)
    }
  }

  const requestMediaAction = (action: NonNullable<typeof pendingMediaAction>) => {
    if (hasSiblings(action.id)) {
      setPendingMediaAction(action)
      setShowSyncMediaModal(true)
    } else {
      executePendingAction(action, false)
    }
  }

  const handleCreate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const formData = new FormData(e.currentTarget)
    const data: CreateInstructorRequest = {
      firstName: formData.get('firstName') as string,
      lastName: formData.get('lastName') as string,
      bio: serializeBio(createBioBlocks) || undefined,
      certifications: createCerts.filter(Boolean).join('\n') || undefined,
      memberType,
      profile8aUrl: create8aUrl.trim() || undefined,
      language: 'pl',
    }
    createMutation.mutate(data)
  }

  const handleUpdate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedMember) return
    const formData = new FormData(e.currentTarget)
    const data: UpdateInstructorRequest = {
      firstName: formData.get('firstName') as string,
      lastName: formData.get('lastName') as string,
      bio: serializeBio(editBioBlocks) || undefined,
      certifications: editCerts.filter(Boolean).join('\n') || undefined,
      active: formData.get('active') === 'on',
      profile8aUrl: edit8aUrl.trim(),
      ...(selectedMember.photoUrl ? { focalPointX: focalPoint.x, focalPointY: focalPoint.y } : {}),
    }
    updateMutation.mutate({ id: selectedMember.id, data })
  }

  const handleFileSelect = (files: File[]) => {
    const file = files[0]
    if (!file) return
    setSelectedFile(file)
    setPhotoPreview(URL.createObjectURL(file))
  }

  useEffect(() => {
    return () => { if (photoPreview) URL.revokeObjectURL(photoPreview) }
  }, [photoPreview])

  const existingLanguages = selectedMember
    ? (allMembers ?? [])
        .filter(m => m.translationGroupId === selectedMember.translationGroupId)
        .map(m => m.language)
    : []
  const availableTargetLanguages = COURSE_CONTENT_LANGUAGES.filter(
    lang => !existingLanguages.includes(lang.code)
  )

  const inputCls = 'w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent'

  if (isLoading) return <div className="flex items-center justify-center py-12"><LoadingSpinner /></div>
  if (error) return <QueryError error={error} />

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <h2 className="text-2xl font-bold text-surface-100">{headerTitle}</h2>
        <Button onClick={() => { setCreateBioBlocks([]); setCreateCerts([]); setCreate8aUrl(''); setCreateModalOpen(true) }}>
          <Plus className="h-4 w-4 mr-2" />
          Dodaj {isInstructor ? 'Instruktora' : 'Zawodnika'}
        </Button>
      </div>

      {/* List */}
      {groups.length === 0 ? (
        <div className="text-center py-12 text-surface-400">
          Brak {isInstructor ? 'instruktorów' : 'zawodników'}. Dodaj pierwszego używając przycisku powyżej.
        </div>
      ) : (
        <div className="space-y-3">
          {groups.map(({ groupId, members: groupMembers }, groupIndex) => {
            const langMap = new Map(groupMembers.map(m => [m.language, m]))
            const displayMember = langMap.get('pl') ?? langMap.get('en') ?? langMap.get('es') ?? groupMembers[0]
            const firstExisting = groupMembers[0]
            const moveMemberId = displayMember.id

            return (
              <div key={groupId} className="flex items-center gap-4 bg-surface-800 border border-surface-700 rounded-lg p-4">
                {/* Photo */}
                <div className="flex-shrink-0 relative">
                  {displayMember.photoUrl ? (
                    <img src={displayMember.photoUrl} alt={displayMember.firstName}
                      className="w-14 h-14 rounded-full object-cover border-2 border-primary-500/20"
                      style={displayMember.focalPointX != null ? { objectPosition: `${displayMember.focalPointX * 100}% ${(displayMember.focalPointY ?? 0.5) * 100}%` } : undefined} />
                  ) : (
                    <div className="w-14 h-14 rounded-full bg-surface-700 border-2 border-surface-600 flex items-center justify-center">
                      <User className="h-7 w-7 text-surface-400" />
                    </div>
                  )}
                  {isInstructor && displayMember.badgeUrl && (
                    <img src={displayMember.badgeUrl} alt="badge" className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full object-contain drop-shadow" />
                  )}
                </div>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className={clsx('text-xs px-2 py-0.5 rounded-full', displayMember.active ? 'bg-green-900/40 text-green-400' : 'bg-rose-900/40 text-rose-400')}>
                      {displayMember.active ? 'Aktywny' : 'Nieaktywny'}
                    </span>
                  </div>
                  <p className="font-medium text-surface-100 truncate">{displayMember.firstName} {displayMember.lastName}</p>
                  <div className="flex items-center gap-1.5 mt-1">
                    {LANG_ORDER.map((lang) => {
                      const member = langMap.get(lang)
                      if (member) {
                        return (
                          <button
                            key={lang}
                            onClick={() => {
                              setSelectedMember(member)
                              setFocalPoint({ x: member.focalPointX ?? 0.5, y: member.focalPointY ?? 0.5 })
                              setEditBioBlocks(deserializeBio(member.bio ?? ''))
                              setEditCerts(member.certifications ? member.certifications.split('\n').filter(Boolean) : [])
                              setEdit8aUrl(member.profile8aUrl ?? '')
                              setEditModalOpen(true)
                            }}
                            title={`Edytuj (${lang.toUpperCase()})`}
                            className={clsx(
                              'text-[10px] font-bold uppercase px-2 py-0.5 rounded border cursor-pointer transition-colors',
                              lang === 'pl' && 'bg-blue-900/40 text-blue-400 border-blue-700 hover:bg-blue-900/60',
                              lang === 'en' && 'bg-green-900/40 text-green-400 border-green-700 hover:bg-green-900/60',
                              lang === 'es' && 'bg-purple-900/40 text-purple-400 border-purple-700 hover:bg-purple-900/60',
                            )}
                          >
                            {lang}
                          </button>
                        )
                      }
                      return (
                        <button
                          key={lang}
                          onClick={() => duplicateFromListMutation.mutate({ id: firstExisting.id, targetLanguage: lang })}
                          disabled={duplicateFromListMutation.isPending}
                          title={t('team.createTranslation', { lang: lang.toUpperCase() })}
                          className={clsx(
                            'text-[10px] font-bold uppercase px-2 py-0.5 rounded border border-dashed cursor-pointer transition-colors',
                            'border-surface-500 text-surface-500 hover:border-surface-300 hover:text-surface-300',
                            duplicateFromListMutation.isPending && 'opacity-50 cursor-wait',
                          )}
                        >
                          +{lang}
                        </button>
                      )
                    })}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-1 flex-shrink-0">
                  <button onClick={() => moveUpMutation.mutate(moveMemberId)}
                    disabled={groupIndex === 0 || moveUpMutation.isPending || moveDownMutation.isPending}
                    className="p-2 text-surface-400 hover:text-surface-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                    <ChevronUp className="h-4 w-4" />
                  </button>
                  <button onClick={() => moveDownMutation.mutate(moveMemberId)}
                    disabled={groupIndex === groups.length - 1 || moveUpMutation.isPending || moveDownMutation.isPending}
                    className="p-2 text-surface-400 hover:text-surface-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                    <ChevronDown className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => setDeleteGroupMembers(groupMembers.map(m => m.id))}
                    title="Usuń"
                    className="p-2 text-surface-400 hover:text-red-400 transition-colors">
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Create Modal ── */}
      <Modal isOpen={createModalOpen} onClose={handleCreateClose} title={`Dodaj ${entityLabel}`} size="lg">
        <form onSubmit={handleCreate} data-form="create-member" className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-200 mb-1">Imię *</label>
            <input type="text" name="firstName" required maxLength={100} className={inputCls} />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-200 mb-1">Nazwisko *</label>
            <input type="text" name="lastName" required maxLength={100} className={inputCls} />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-200 mb-2">{certLabel}</label>
            <CertificationsEditor items={createCerts} onChange={setCreateCerts} addLabel={certAddLabel} />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-200 mb-1">Profil na 8a.nu</label>
            <input
              type="url"
              value={create8aUrl}
              onChange={(e) => setCreate8aUrl(e.target.value)}
              placeholder="https://www.8a.nu/user/..."
              className={inputCls}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-200 mb-2">Bio</label>
            <BioBlockEditor blocks={createBioBlocks} onChange={setCreateBioBlocks} />
          </div>
          <div className="flex items-center gap-2 text-sm text-surface-400">
            <span>{t('team.language')}:</span>
            <LanguageBadge language="pl" />
          </div>
          <div className="flex gap-2 justify-end">
            <Button type="button" variant="ghost" onClick={handleCreateClose}>Anuluj</Button>
            <Button type="submit" loading={createMutation.isPending}>Dodaj</Button>
          </div>
          {createMutation.error && <div className="text-rose-400 text-sm">{String(createMutation.error)}</div>}
        </form>
      </Modal>

      {/* ── Edit Modal ── */}
      {selectedMember && (
        <Modal isOpen={editModalOpen} onClose={handleEditClose} title={`Edytuj ${entityLabel}`} size="lg">
          <form ref={editFormRef} onSubmit={handleUpdate} className="space-y-4">
            {/* Language badge + duplication */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-sm text-surface-400">{t('team.language')}:</span>
                <LanguageBadge language={selectedMember.language} />
              </div>
            </div>

            {/* Duplicate as translation */}
            <div className="bg-surface-700/50 rounded-lg p-3 space-y-2">
              <h4 className="text-sm font-medium text-surface-300 flex items-center gap-1.5">
                <Copy className="w-3.5 h-3.5" />
                {t('team.duplicateAsTranslation')}
              </h4>
              {availableTargetLanguages.length === 0 ? (
                <p className="text-xs text-surface-500">{t('team.translationExists')}</p>
              ) : (
                <div className="flex gap-2">
                  {availableTargetLanguages.map((lang) => (
                    <Button key={lang.code} variant="secondary" size="sm"
                      loading={duplicateMutation.isPending}
                      onClick={() => duplicateMutation.mutate({ id: selectedMember.id, targetLanguage: lang.code })}>
                      {lang.label}
                    </Button>
                  ))}
                </div>
              )}
              {duplicateMutation.error && <div className="text-rose-400 text-xs">{String(duplicateMutation.error)}</div>}
            </div>

            {/* Photo */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-surface-200">Zdjęcie profilowe</label>
                <Button type="button" variant="ghost" size="sm"
                  onClick={() => { setUploadPhotoModalOpen(true) }}>
                  <Upload className="w-3.5 h-3.5 mr-1" /> Zmień zdjęcie
                </Button>
              </div>
              {selectedMember.photoUrl ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-3">
                    <img src={selectedMember.photoUrl} alt={selectedMember.firstName}
                      className="w-16 h-16 rounded-full object-cover border-2 border-primary-500/20 shrink-0"
                      style={selectedMember.focalPointX != null ? { objectPosition: `${selectedMember.focalPointX * 100}% ${(selectedMember.focalPointY ?? 0.5) * 100}%` } : undefined} />
                    <div className="flex flex-col gap-1">
                      <span className="text-xs text-surface-400">Podgląd · przesuń punkt ostrości poniżej</span>
                      <button type="button" onClick={() => { if (confirm('Czy na pewno usunąć zdjęcie?')) requestMediaAction({ type: 'deletePhoto', id: selectedMember.id }) }}
                        className="text-xs text-rose-400 hover:text-rose-300 text-left transition-colors">
                        Usuń zdjęcie
                      </button>
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-surface-400">Kadrowanie</span>
                    <button type="button" onClick={() => setFocalPoint({ x: 0.5, y: 0.5 })}
                      className="text-xs text-primary-400 hover:text-primary-300 transition-colors">Resetuj</button>
                  </div>
                  <FocalPointEditor imageUrl={selectedMember.photoUrl} value={focalPoint} onChange={setFocalPoint} aspectRatio="1/1" className="max-h-[50vh]" />
                </div>
              ) : (
                <div className="flex items-center gap-3 py-2">
                  <div className="w-16 h-16 rounded-full bg-surface-700 border-2 border-surface-600 flex items-center justify-center shrink-0">
                    <User className="h-8 w-8 text-surface-400" />
                  </div>
                  <span className="text-sm text-surface-400">Brak zdjęcia — kliknij "Zmień zdjęcie" aby dodać</span>
                </div>
              )}
            </div>

            {isInstructor && (
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="block text-sm font-medium text-surface-200">Naklejka / badge</label>
                </div>
                <div className="flex items-center gap-3">
                  {selectedMember.badgeUrl && (
                    <img src={selectedMember.badgeUrl} alt="badge" className="w-10 h-10 rounded-full object-contain" />
                  )}
                  <Button type="button" variant="ghost" size="sm"
                    onClick={() => { setBadgeTargetId(selectedMember.id); setBadgePickerOpen(true) }}>
                    <Library className="w-3.5 h-3.5 mr-1" /> Wybierz z biblioteki
                  </Button>
                  {selectedMember.badgeUrl && (
                    <Button type="button" variant="ghost" size="sm"
                      onClick={() => requestMediaAction({ type: 'deleteBadge', id: selectedMember.id })}
                      loading={deleteBadgeMutation.isPending}>
                      <Trash2 className="w-3.5 h-3.5 text-rose-400 mr-1" /> Usuń
                    </Button>
                  )}
                </div>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">Imię *</label>
              <input type="text" name="firstName" defaultValue={selectedMember.firstName} required maxLength={100} className={inputCls} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">Nazwisko *</label>
              <input type="text" name="lastName" defaultValue={selectedMember.lastName} required maxLength={100} className={inputCls} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-200 mb-2">{certLabel}</label>
              <CertificationsEditor items={editCerts} onChange={setEditCerts} addLabel={certAddLabel} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">Profil na 8a.nu</label>
              <input
                type="url"
                value={edit8aUrl}
                onChange={(e) => setEdit8aUrl(e.target.value)}
                placeholder="https://www.8a.nu/user/..."
                className={inputCls}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-200 mb-2">Bio</label>
              <BioBlockEditor blocks={editBioBlocks} onChange={setEditBioBlocks} />
            </div>

            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" name="active" defaultChecked={selectedMember.active}
                className="w-4 h-4 rounded border-surface-600 bg-surface-700 text-primary-500 focus:ring-2 focus:ring-primary-500" />
              <span className="text-sm text-surface-200">Aktywny</span>
              <span className="text-xs text-surface-500">(wszystkie języki)</span>
            </label>

            {existingLanguages.length > 1 && (
              <div className="pt-4 border-t border-surface-600">
                <button
                  type="button"
                  onClick={() => setDeleteSingleConfirm(true)}
                  className="text-sm text-red-400 hover:text-red-300 transition-colors"
                >
                  {t('team.deleteSingleVersion', { lang: selectedMember.language.toUpperCase() })}
                </button>
              </div>
            )}

            <div className="flex gap-2 justify-end">
              <Button type="button" variant="ghost" onClick={handleEditClose}>Anuluj</Button>
              <Button type="submit" loading={updateMutation.isPending}>Zapisz</Button>
            </div>
            {updateMutation.error && <div className="text-rose-400 text-sm">{String(updateMutation.error)}</div>}
          </form>
        </Modal>
      )}

      {/* ── Upload Photo Modal ── */}
      {selectedMember && (
        <Modal isOpen={uploadPhotoModalOpen}
          onClose={() => { setUploadPhotoModalOpen(false); setSelectedMember(null); setPhotoPreview(null); setSelectedFile(null) }}
          title="Prześlij Zdjęcie">
          <div className="space-y-4">
            <FileUpload onFileSelect={handleFileSelect}
              onClear={() => { setPhotoPreview(null); setSelectedFile(null) }}
              previews={photoPreview ? [photoPreview] : []} />
            <div className="flex items-center gap-2">
              <span className="text-xs text-surface-400">lub wybierz:</span>
              <Button type="button" variant="ghost" size="sm"
                onClick={() => { setPhotoGalleryOpen(true) }}>
                <Images className="w-4 h-4 mr-1" /> Z galerii
              </Button>
            </div>
            <div className="flex gap-2 justify-end">
              <Button type="button" variant="ghost" onClick={() => {
                setUploadPhotoModalOpen(false); setSelectedMember(null); setPhotoPreview(null); setSelectedFile(null)
              }}>Anuluj</Button>
              <Button onClick={() => { if (selectedMember && selectedFile) requestMediaAction({ type: 'uploadPhoto', id: selectedMember.id, file: selectedFile }) }}
                disabled={!selectedFile} loading={uploadPhotoMutation.isPending}>Prześlij</Button>
            </div>
            {uploadPhotoMutation.error && <div className="text-rose-400 text-sm">{String(uploadPhotoMutation.error)}</div>}
          </div>
        </Modal>
      )}

      {/* Badge picker */}
      <MediaPickerModal isOpen={badgePickerOpen} onClose={() => setBadgePickerOpen(false)}
        onSelect={(asset) => {
          if (badgeTargetId) requestMediaAction({ type: 'setBadge', id: badgeTargetId, badgeUrl: asset.url })
          setBadgePickerOpen(false)
        }} />

      {/* Photo gallery picker */}
      <GalleryPickerModal isOpen={photoGalleryOpen} onClose={() => setPhotoGalleryOpen(false)}
        onSelect={(url) => {
          if (selectedMember) requestMediaAction({ type: 'setPhotoUrl', id: selectedMember.id, photoUrl: url })
          setPhotoGalleryOpen(false)
        }} />

      {/* Delete group confirm */}
      <ConfirmModal isOpen={!!deleteGroupMembers}
        onClose={() => setDeleteGroupMembers(null)}
        onConfirm={() => deleteGroupMembers && deleteGroupMutation.mutate(deleteGroupMembers)}
        title={`Usuń ${entityLabel}`}
        message={t('team.deleteGroupConfirmMessage')}
        confirmText="Usuń" />

      {/* Delete single language version confirm */}
      <ConfirmModal isOpen={deleteSingleConfirm}
        onClose={() => setDeleteSingleConfirm(false)}
        onConfirm={() => { if (selectedMember) deleteSingleMutation.mutate(selectedMember.id) }}
        title={t('team.deleteSingleConfirmTitle')}
        message={t('team.deleteSingleConfirmMessage', { lang: selectedMember?.language.toUpperCase(), name: `${selectedMember?.firstName} ${selectedMember?.lastName}` })}
        confirmText={t('team.deleteSingleConfirm')} />

      {/* Create exit confirm (no save option — new unsaved item should not be persisted) */}
      <ConfirmModal isOpen={showCreateExitConfirm}
        onClose={() => setShowCreateExitConfirm(false)}
        onConfirm={() => { setShowCreateExitConfirm(false); setCreateModalOpen(false); setCreateBioBlocks([]); setCreateCerts([]); setCreate8aUrl('') }}
        title={t('team.exitConfirmTitle')}
        message={t('team.exitConfirmMessage')}
        confirmText={t('team.exitConfirm')}
        variant="primary" />

      {/* Edit exit confirm (with save option) */}
      <ConfirmModal isOpen={showEditExitConfirm}
        onClose={() => setShowEditExitConfirm(false)}
        onConfirm={() => { setShowEditExitConfirm(false); setEditModalOpen(false); setSelectedMember(null) }}
        onSave={() => { setShowEditExitConfirm(false); handleEditSaveAndClose() }}
        title={t('team.exitConfirmTitle')}
        message={t('team.exitConfirmMessage')}
        confirmText={t('team.exitConfirm')}
        saveText={t('team.exitConfirmSave')}
        variant="primary" />

      {/* Sync media to translations */}
      <Modal isOpen={showSyncMediaModal} onClose={() => { setShowSyncMediaModal(false); setPendingMediaAction(null) }}
        title={t('team.syncMediaModalTitle')}>
        <p className="text-sm text-surface-300 mb-4">{t('team.syncMediaModalMessage')}</p>
        {syncMediaMutation.isError && (
          <p className="text-sm text-red-400 mb-4">{String(syncMediaMutation.error)}</p>
        )}
        <div className="flex gap-3 justify-end">
          <Button variant="ghost" size="sm"
            disabled={uploadPhotoMutation.isPending || deletePhotoMutation.isPending || setBadgeMutation.isPending || deleteBadgeMutation.isPending || setPhotoUrlMutation.isPending || syncMediaMutation.isPending}
            onClick={() => { setShowSyncMediaModal(false); setPendingMediaAction(null) }}>
            {t('team.syncMediaCancel')}
          </Button>
          <Button variant="secondary" size="sm"
            disabled={uploadPhotoMutation.isPending || deletePhotoMutation.isPending || setBadgeMutation.isPending || deleteBadgeMutation.isPending || setPhotoUrlMutation.isPending || syncMediaMutation.isPending}
            loading={!syncMediaMutation.isPending && (uploadPhotoMutation.isPending || deletePhotoMutation.isPending || setBadgeMutation.isPending || deleteBadgeMutation.isPending || setPhotoUrlMutation.isPending)}
            onClick={async () => {
              if (pendingMediaAction) {
                await executePendingAction(pendingMediaAction, false)
              }
              setShowSyncMediaModal(false)
              setPendingMediaAction(null)
            }}>
            {t('team.syncMediaSkip')}
          </Button>
          <Button variant="primary" size="sm"
            onClick={async () => {
              if (pendingMediaAction) {
                await executePendingAction(pendingMediaAction, true)
              }
              setShowSyncMediaModal(false)
              setPendingMediaAction(null)
            }}
            disabled={uploadPhotoMutation.isPending || deletePhotoMutation.isPending || setBadgeMutation.isPending || deleteBadgeMutation.isPending || setPhotoUrlMutation.isPending || syncMediaMutation.isPending}
            loading={syncMediaMutation.isPending || uploadPhotoMutation.isPending || deletePhotoMutation.isPending || setBadgeMutation.isPending || deleteBadgeMutation.isPending || setPhotoUrlMutation.isPending}>
            {t('team.syncMediaConfirm')}
          </Button>
        </div>
      </Modal>
    </div>
  )
}
