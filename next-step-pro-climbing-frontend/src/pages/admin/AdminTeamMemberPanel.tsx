import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { User, Upload, Pencil, Trash2, Plus, Library, Images, X, ChevronUp, ChevronDown } from 'lucide-react'
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
import { BioBlockEditor, deserializeBio, serializeBio } from '../../components/ui/BioBlockEditor'
import type { BioBlock } from '../../components/ui/BioBlockEditor'

// ─── Certifications list editor ───────────────────────────────────────────────

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
            className="flex-1 px-3 py-1.5 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
          <button type="button" onClick={() => remove(idx)} className="p-1 text-dark-500 hover:text-rose-400 transition-colors">
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

// ─── Main panel ───────────────────────────────────────────────────────────────

interface Props { memberType: InstructorType }

export function AdminTeamMemberPanel({ memberType }: Props) {
  const isInstructor = memberType === 'INSTRUCTOR'
  const entityLabel = isInstructor ? 'Instruktora' : 'Zawodnika'
  const certLabel = isInstructor ? 'Certyfikaty' : 'Osiągnięcia'
  const certAddLabel = isInstructor ? 'Dodaj certyfikat' : 'Dodaj osiągnięcie'
  const headerTitle = isInstructor ? 'Zarządzanie Instruktorami' : 'Zarządzanie Zawodnikami'

  const queryClient = useQueryClient()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [uploadPhotoModalOpen, setUploadPhotoModalOpen] = useState(false)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [selectedMember, setSelectedMember] = useState<InstructorAdmin | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [focalPoint, setFocalPoint] = useState({ x: 0.5, y: 0.5 })

  // Form state
  const [createBioBlocks, setCreateBioBlocks] = useState<BioBlock[]>([])
  const [editBioBlocks, setEditBioBlocks] = useState<BioBlock[]>([])
  const [createCerts, setCreateCerts] = useState<string[]>([])
  const [editCerts, setEditCerts] = useState<string[]>([])
  const [create8aUrl, setCreate8aUrl] = useState('')
  const [edit8aUrl, setEdit8aUrl] = useState('')

  // Pickers (badge + photo only — bio pickers live inside BioBlockEditor)
  const [badgePickerOpen, setBadgePickerOpen] = useState(false)
  const [badgeTargetId, setBadgeTargetId] = useState<string | null>(null)
  const [photoGalleryOpen, setPhotoGalleryOpen] = useState(false)

  const { data: allMembers, isLoading, error } = useQuery({
    queryKey: ['admin', 'instructors'],
    queryFn: adminInstructorApi.getAll,
  })
  const members = (allMembers ?? []).filter((m) => m.memberType === memberType)

  const createMutation = useMutation({
    mutationFn: adminInstructorApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setCreateModalOpen(false)
      setCreateBioBlocks([])
      setCreateCerts([])
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateInstructorRequest }) =>
      adminInstructorApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setEditModalOpen(false)
      setSelectedMember(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: adminInstructorApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setDeleteConfirmOpen(false)
      setSelectedMember(null)
    },
  })

  const uploadPhotoMutation = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => adminInstructorApi.uploadPhoto(id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setUploadPhotoModalOpen(false)
      setSelectedMember(null)
      setPhotoPreview(null)
      setSelectedFile(null)
    },
  })

  const deletePhotoMutation = useMutation({
    mutationFn: adminInstructorApi.deletePhoto,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] }) },
  })

  const setBadgeMutation = useMutation({
    mutationFn: ({ id, badgeUrl }: { id: string; badgeUrl: string }) => adminInstructorApi.setBadge(id, badgeUrl),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] }) },
  })

  const deleteBadgeMutation = useMutation({
    mutationFn: adminInstructorApi.deleteBadge,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] }) },
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
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setUploadPhotoModalOpen(false)
      setSelectedMember(null)
    },
  })

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

  const inputCls = 'w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent'

  if (isLoading) return <div className="flex items-center justify-center py-12"><LoadingSpinner /></div>
  if (error) return <QueryError error={error} />

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-dark-100">{headerTitle}</h2>
        <Button onClick={() => { setCreateBioBlocks([]); setCreateCerts([]); setCreate8aUrl(''); setCreateModalOpen(true) }}>
          <Plus className="h-4 w-4 mr-2" />
          Dodaj {isInstructor ? 'Instruktora' : 'Zawodnika'}
        </Button>
      </div>

      {/* List */}
      {members.length === 0 ? (
        <div className="text-center py-12 text-dark-400">
          Brak {isInstructor ? 'instruktorów' : 'zawodników'}. Dodaj pierwszego używając przycisku powyżej.
        </div>
      ) : (
        <div className="space-y-4">
          {members.map((member, idx) => (
            <div key={member.id} className="bg-dark-800 rounded-lg p-6 border border-dark-700">
              <div className="flex gap-6">
                {/* Arrows */}
                <div className="flex flex-col items-center justify-center gap-1 shrink-0">
                  <button onClick={() => moveUpMutation.mutate(member.id)}
                    disabled={idx === 0 || moveUpMutation.isPending || moveDownMutation.isPending}
                    className="p-1 rounded text-dark-400 hover:text-dark-100 hover:bg-dark-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
                    <ChevronUp className="w-5 h-5" />
                  </button>
                  <button onClick={() => moveDownMutation.mutate(member.id)}
                    disabled={idx === members.length - 1 || moveUpMutation.isPending || moveDownMutation.isPending}
                    className="p-1 rounded text-dark-400 hover:text-dark-100 hover:bg-dark-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
                    <ChevronDown className="w-5 h-5" />
                  </button>
                </div>

                {/* Photo */}
                <div className="flex-shrink-0 flex flex-col items-center gap-2">
                  <div className="relative group">
                    {member.photoUrl ? (
                      <img src={member.photoUrl} alt={member.firstName}
                        className="w-32 h-32 rounded-full object-cover border-2 border-primary-500/20"
                        style={member.focalPointX != null ? { objectPosition: `${member.focalPointX * 100}% ${(member.focalPointY ?? 0.5) * 100}%` } : undefined} />
                    ) : (
                      <div className="w-32 h-32 rounded-full bg-dark-700 border-2 border-dark-600 flex items-center justify-center">
                        <User className="h-16 w-16 text-dark-400" />
                      </div>
                    )}
                    {isInstructor && member.badgeUrl && (
                      <img src={member.badgeUrl} alt="badge" className="absolute bottom-0 right-0 w-7 h-7 rounded-full object-contain drop-shadow" />
                    )}
                    <button onClick={() => { setSelectedMember(member); setUploadPhotoModalOpen(true) }}
                      className="absolute inset-0 bg-black/60 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <Upload className="h-6 w-6 text-white" />
                    </button>
                  </div>

                  {isInstructor && (
                    <div className="flex flex-col items-center gap-1">
                      <span className="text-xs text-dark-400">Naklejka</span>
                      <div className="flex gap-1">
                        <Button size="sm" variant="ghost" title="Wybierz z biblioteki"
                          onClick={() => { setBadgeTargetId(member.id); setBadgePickerOpen(true) }}>
                          <Library className="w-3.5 h-3.5" />
                        </Button>
                        {member.badgeUrl && (
                          <Button size="sm" variant="ghost" title="Usuń naklejkę"
                            onClick={() => deleteBadgeMutation.mutate(member.id)}
                            loading={deleteBadgeMutation.isPending && deleteBadgeMutation.variables === member.id}>
                            <Trash2 className="w-3.5 h-3.5 text-rose-400" />
                          </Button>
                        )}
                      </div>
                    </div>
                  )}
                </div>

                {/* Info */}
                <div className="flex-1 space-y-3">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-xl font-bold text-dark-100">{member.firstName} {member.lastName}</h3>
                      <span className={`text-sm ${member.active ? 'text-green-400' : 'text-rose-400'}`}>
                        {member.active ? '✓ Aktywny' : '✕ Nieaktywny'}
                      </span>
                    </div>
                    <div className="flex gap-2">
                      <Button variant="ghost" size="sm" onClick={() => {
                        setSelectedMember(member)
                        setFocalPoint({ x: member.focalPointX ?? 0.5, y: member.focalPointY ?? 0.5 })
                        setEditBioBlocks(deserializeBio(member.bio ?? ''))
                        setEditCerts(member.certifications ? member.certifications.split('\n').filter(Boolean) : [])
                        setEdit8aUrl(member.profile8aUrl ?? '')
                        setEditModalOpen(true)
                      }}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="danger" size="sm" onClick={() => { setSelectedMember(member); setDeleteConfirmOpen(true) }}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>

                  {member.certifications && (
                    <div>
                      <h4 className="text-sm font-medium text-dark-300 mb-1.5">{certLabel}:</h4>
                      <ul className="space-y-1">
                        {member.certifications.split('\n').filter(Boolean).map((line, i) => (
                          <li key={i} className="flex items-start gap-2 text-dark-200 text-sm">
                            <span className="text-primary-400 shrink-0 mt-0.5">•</span>
                            <span>{line}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {member.bio && (
                    <div>
                      <h4 className="text-sm font-medium text-dark-300 mb-1">Bio:</h4>
                      <p className="text-dark-200 text-sm line-clamp-2 text-dark-500 italic">
                        {deserializeBio(member.bio).filter(b => b.type === 'text').map(b => b.type === 'text' ? b.content : '').join(' ').slice(0, 120) || '(tylko zdjęcia)'}…
                      </p>
                    </div>
                  )}
                  {member.profile8aUrl && (
                    <a href={member.profile8aUrl} target="_blank" rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-orange-400 hover:text-orange-300 transition-colors">
                      <span className="font-bold">8a.nu</span>
                      <span className="text-dark-500">↗</span>
                    </a>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Create Modal ── */}
      <Modal isOpen={createModalOpen} onClose={() => setCreateModalOpen(false)} title={`Dodaj ${entityLabel}`} size="lg">
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">Imię *</label>
            <input type="text" name="firstName" required maxLength={100} className={inputCls} />
          </div>
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">Nazwisko *</label>
            <input type="text" name="lastName" required maxLength={100} className={inputCls} />
          </div>
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-2">{certLabel}</label>
            <CertificationsEditor items={createCerts} onChange={setCreateCerts} addLabel={certAddLabel} />
          </div>
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">Profil na 8a.nu</label>
            <input
              type="url"
              value={create8aUrl}
              onChange={(e) => setCreate8aUrl(e.target.value)}
              placeholder="https://www.8a.nu/user/..."
              className={inputCls}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-2">Bio</label>
            <BioBlockEditor blocks={createBioBlocks} onChange={setCreateBioBlocks} />
          </div>
          <div className="flex gap-2 justify-end">
            <Button type="button" variant="ghost" onClick={() => setCreateModalOpen(false)}>Anuluj</Button>
            <Button type="submit" loading={createMutation.isPending}>Dodaj</Button>
          </div>
          {createMutation.error && <div className="text-rose-400 text-sm">{String(createMutation.error)}</div>}
        </form>
      </Modal>

      {/* ── Edit Modal ── */}
      {selectedMember && (
        <Modal isOpen={editModalOpen} onClose={() => { setEditModalOpen(false); setSelectedMember(null) }} title={`Edytuj ${entityLabel}`} size="lg">
          <form onSubmit={handleUpdate} className="space-y-4">
            {/* Zdjęcie profilowe — na górze */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-dark-200">Zdjęcie profilowe</label>
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
                      <span className="text-xs text-dark-400">Podgląd · przesuń punkt ostrości poniżej</span>
                      <button type="button" onClick={() => { if (confirm('Czy na pewno usunąć zdjęcie?')) deletePhotoMutation.mutate(selectedMember.id) }}
                        className="text-xs text-rose-400 hover:text-rose-300 text-left transition-colors">
                        Usuń zdjęcie
                      </button>
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-dark-400">Kadrowanie</span>
                    <button type="button" onClick={() => setFocalPoint({ x: 0.5, y: 0.5 })}
                      className="text-xs text-primary-400 hover:text-primary-300 transition-colors">Resetuj</button>
                  </div>
                  <FocalPointEditor imageUrl={selectedMember.photoUrl} value={focalPoint} onChange={setFocalPoint} aspectRatio="1/1" />
                </div>
              ) : (
                <div className="flex items-center gap-3 py-2">
                  <div className="w-16 h-16 rounded-full bg-dark-700 border-2 border-dark-600 flex items-center justify-center shrink-0">
                    <User className="h-8 w-8 text-dark-400" />
                  </div>
                  <span className="text-sm text-dark-400">Brak zdjęcia — kliknij "Zmień zdjęcie" aby dodać</span>
                </div>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">Imię *</label>
              <input type="text" name="firstName" defaultValue={selectedMember.firstName} required maxLength={100} className={inputCls} />
            </div>
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">Nazwisko *</label>
              <input type="text" name="lastName" defaultValue={selectedMember.lastName} required maxLength={100} className={inputCls} />
            </div>
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-2">{certLabel}</label>
              <CertificationsEditor items={editCerts} onChange={setEditCerts} addLabel={certAddLabel} />
            </div>
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">Profil na 8a.nu</label>
              <input
                type="url"
                value={edit8aUrl}
                onChange={(e) => setEdit8aUrl(e.target.value)}
                placeholder="https://www.8a.nu/user/..."
                className={inputCls}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-2">Bio</label>
              <BioBlockEditor blocks={editBioBlocks} onChange={setEditBioBlocks} />
            </div>

            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" name="active" defaultChecked={selectedMember.active}
                className="w-4 h-4 rounded border-dark-600 bg-dark-700 text-primary-500 focus:ring-2 focus:ring-primary-500" />
              <span className="text-sm text-dark-200">Aktywny</span>
            </label>

            <div className="flex gap-2 justify-end">
              <Button type="button" variant="ghost" onClick={() => { setEditModalOpen(false); setSelectedMember(null) }}>Anuluj</Button>
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
              <span className="text-xs text-dark-400">lub wybierz:</span>
              <Button type="button" variant="ghost" size="sm"
                onClick={() => { setPhotoGalleryOpen(true) }}>
                <Images className="w-4 h-4 mr-1" /> Z galerii
              </Button>
            </div>
            <div className="flex gap-2 justify-end">
              <Button type="button" variant="ghost" onClick={() => {
                setUploadPhotoModalOpen(false); setSelectedMember(null); setPhotoPreview(null); setSelectedFile(null)
              }}>Anuluj</Button>
              <Button onClick={() => { if (selectedMember && selectedFile) uploadPhotoMutation.mutate({ id: selectedMember.id, file: selectedFile }) }}
                disabled={!selectedFile} loading={uploadPhotoMutation.isPending}>Prześlij</Button>
            </div>
            {uploadPhotoMutation.error && <div className="text-rose-400 text-sm">{String(uploadPhotoMutation.error)}</div>}
          </div>
        </Modal>
      )}

      {/* Badge picker */}
      <MediaPickerModal isOpen={badgePickerOpen} onClose={() => setBadgePickerOpen(false)}
        onSelect={(asset) => {
          if (badgeTargetId) setBadgeMutation.mutate({ id: badgeTargetId, badgeUrl: asset.url })
          setBadgePickerOpen(false)
        }} />

      {/* Photo gallery picker */}
      <GalleryPickerModal isOpen={photoGalleryOpen} onClose={() => setPhotoGalleryOpen(false)}
        onSelect={(url) => {
          if (selectedMember) setPhotoUrlMutation.mutate({ id: selectedMember.id, photoUrl: url })
          setPhotoGalleryOpen(false)
        }} />

      {/* Delete Confirm */}
      <ConfirmModal isOpen={deleteConfirmOpen}
        onClose={() => { setDeleteConfirmOpen(false); setSelectedMember(null) }}
        onConfirm={() => { if (selectedMember) deleteMutation.mutate(selectedMember.id) }}
        title={`Usuń ${entityLabel}`}
        message={`Czy na pewno chcesz usunąć ${isInstructor ? 'instruktora' : 'zawodnika'} ${selectedMember?.firstName} ${selectedMember?.lastName}? Tej operacji nie można cofnąć.`}
        confirmText="Usuń" />
    </div>
  )
}
