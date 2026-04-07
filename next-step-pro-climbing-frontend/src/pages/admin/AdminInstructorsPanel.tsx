import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { User, Upload, Pencil, Trash2, Plus, Library } from 'lucide-react'
import { adminInstructorApi } from '../../api/client'
import type { InstructorAdmin, CreateInstructorRequest, UpdateInstructorRequest } from '../../types'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { FileUpload } from '../../components/ui/FileUpload'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'

export function AdminInstructorsPanel() {
  const queryClient = useQueryClient()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [uploadPhotoModalOpen, setUploadPhotoModalOpen] = useState(false)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [selectedInstructor, setSelectedInstructor] = useState<InstructorAdmin | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [focalPoint, setFocalPoint] = useState({ x: 0.5, y: 0.5 })
  const [createBio, setCreateBio] = useState('')
  const [editBio, setEditBio] = useState('')
  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [pickerTarget, setPickerTarget] = useState<'create' | 'edit' | 'badge'>('edit')
  const [badgeTargetId, setBadgeTargetId] = useState<string | null>(null)
  const createBioRef = useRef<HTMLTextAreaElement>(null)
  const editBioRef = useRef<HTMLTextAreaElement>(null)

  const { data: instructors, isLoading, error } = useQuery({
    queryKey: ['admin', 'instructors'],
    queryFn: adminInstructorApi.getAll,
  })

  const createMutation = useMutation({
    mutationFn: adminInstructorApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setCreateModalOpen(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateInstructorRequest }) =>
      adminInstructorApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setEditModalOpen(false)
      setSelectedInstructor(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: adminInstructorApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setDeleteConfirmOpen(false)
      setSelectedInstructor(null)
    },
  })

  const uploadPhotoMutation = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) =>
      adminInstructorApi.uploadPhoto(id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
      setUploadPhotoModalOpen(false)
      setSelectedInstructor(null)
      setPhotoPreview(null)
      setSelectedFile(null)
    },
  })

  const deletePhotoMutation = useMutation({
    mutationFn: adminInstructorApi.deletePhoto,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
    },
  })

  const setBadgeMutation = useMutation({
    mutationFn: ({ id, badgeUrl }: { id: string; badgeUrl: string }) =>
      adminInstructorApi.setBadge(id, badgeUrl),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
    },
  })

  const deleteBadgeMutation = useMutation({
    mutationFn: adminInstructorApi.deleteBadge,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
    },
  })

  const insertImageAtCursor = (url: string, target: 'create' | 'edit') => {
    const ref = target === 'create' ? createBioRef : editBioRef
    const setter = target === 'create' ? setCreateBio : setEditBio
    const el = ref.current
    const insert = `\n![](${url})\n`
    if (el) {
      const start = el.selectionStart
      const end = el.selectionEnd
      setter((prev) => prev.slice(0, start) + insert + prev.slice(end))
      setTimeout(() => {
        el.selectionStart = el.selectionEnd = start + insert.length
        el.focus()
      }, 0)
    } else {
      setter((prev) => prev + insert)
    }
  }

  const handleCreate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const formData = new FormData(e.currentTarget)
    const data: CreateInstructorRequest = {
      firstName: formData.get('firstName') as string,
      lastName: formData.get('lastName') as string,
      bio: createBio || undefined,
      certifications: formData.get('certifications') as string || undefined,
    }
    createMutation.mutate(data)
  }

  const handleUpdate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedInstructor) return
    const formData = new FormData(e.currentTarget)
    const data: UpdateInstructorRequest = {
      firstName: formData.get('firstName') as string,
      lastName: formData.get('lastName') as string,
      bio: editBio || undefined,
      certifications: formData.get('certifications') as string || undefined,
      active: formData.get('active') === 'on',
      displayOrder: parseInt(formData.get('displayOrder') as string) || 0,
      ...(selectedInstructor.photoUrl ? { focalPointX: focalPoint.x, focalPointY: focalPoint.y } : {}),
    }
    updateMutation.mutate({ id: selectedInstructor.id, data })
  }

  const handleUploadPhoto = () => {
    if (!selectedInstructor || !selectedFile) return
    uploadPhotoMutation.mutate({ id: selectedInstructor.id, file: selectedFile })
  }

  const handleFileSelect = (files: File[]) => {
    const file = files[0] // Only take first file for instructors
    if (!file) return
    setSelectedFile(file)

    // Use createObjectURL instead of FileReader for memory efficiency
    // This creates lightweight blob URL without loading file into memory
    const previewUrl = URL.createObjectURL(file)
    setPhotoPreview(previewUrl)
  }

  // Cleanup: revoke blob URL when component unmounts or preview changes
  useEffect(() => {
    return () => {
      if (photoPreview) {
        URL.revokeObjectURL(photoPreview)
      }
    }
  }, [photoPreview])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return <QueryError error={error} />
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-dark-100">Zarządzanie Instruktorami</h2>
        <Button onClick={() => { setCreateBio(''); setCreateModalOpen(true) }}>
          <Plus className="h-4 w-4 mr-2" />
          Dodaj instruktora
        </Button>
      </div>

      {/* Instructors List */}
      {!instructors || instructors.length === 0 ? (
        <div className="text-center py-12 text-dark-400">
          Brak instruktorów. Dodaj pierwszego używając przycisku powyżej.
        </div>
      ) : (
        <div className="space-y-4">
          {instructors.map((instructor) => (
            <div
              key={instructor.id}
              className="bg-dark-800 rounded-lg p-6 border border-dark-700"
            >
              <div className="flex gap-6">
                {/* Photo + Badge */}
                <div className="flex-shrink-0 flex flex-col items-center gap-2">
                  <div className="relative group">
                    {instructor.photoUrl ? (
                      <img
                        src={instructor.photoUrl}
                        alt={instructor.firstName}
                        className="w-32 h-32 rounded-full object-cover border-2 border-primary-500/20"
                        style={instructor.focalPointX != null ? { objectPosition: `${instructor.focalPointX * 100}% ${(instructor.focalPointY ?? 0.5) * 100}%` } : undefined}
                      />
                    ) : (
                      <div className="w-32 h-32 rounded-full bg-dark-700 border-2 border-dark-600 flex items-center justify-center">
                        <User className="h-16 w-16 text-dark-400" />
                      </div>
                    )}
                    {instructor.badgeUrl && (
                      <img
                        src={instructor.badgeUrl}
                        alt="badge"
                        className="absolute bottom-0 right-0 w-9 h-9 rounded-full object-contain bg-white border border-dark-600/40 shadow p-px"
                      />
                    )}
                    <button
                      onClick={() => {
                        setSelectedInstructor(instructor)
                        setUploadPhotoModalOpen(true)
                      }}
                      className="absolute inset-0 bg-black/60 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <Upload className="h-6 w-6 text-white" />
                    </button>
                  </div>

                  {/* Badge controls */}
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-xs text-dark-400">Naklejka</span>
                    <div className="flex gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        title="Wybierz z biblioteki"
                        onClick={() => {
                          setBadgeTargetId(instructor.id)
                          setPickerTarget('badge')
                          setShowMediaPicker(true)
                        }}
                      >
                        <Library className="w-3.5 h-3.5" />
                      </Button>
                      {instructor.badgeUrl && (
                        <Button
                          size="sm"
                          variant="ghost"
                          title="Usuń naklejkę"
                          onClick={() => deleteBadgeMutation.mutate(instructor.id)}
                          loading={deleteBadgeMutation.isPending && deleteBadgeMutation.variables === instructor.id}
                        >
                          <Trash2 className="w-3.5 h-3.5 text-rose-400" />
                        </Button>
                      )}
                    </div>
                  </div>
                </div>

                {/* Info */}
                <div className="flex-1 space-y-3">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-xl font-bold text-dark-100">
                        {instructor.firstName} {instructor.lastName}
                      </h3>
                      <div className="flex items-center gap-3 mt-1">
                        <span className={`text-sm ${instructor.active ? 'text-green-400' : 'text-rose-400'}`}>
                          {instructor.active ? '✓ Aktywny' : '✕ Nieaktywny'}
                        </span>
                        <span className="text-sm text-dark-400">
                          Kolejność: {instructor.displayOrder}
                        </span>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          setSelectedInstructor(instructor)
                          setFocalPoint({ x: instructor.focalPointX ?? 0.5, y: instructor.focalPointY ?? 0.5 })
                          setEditBio(instructor.bio ?? '')
                          setEditModalOpen(true)
                        }}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      {instructor.photoUrl && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            if (confirm('Czy na pewno usunąć zdjęcie?')) {
                              deletePhotoMutation.mutate(instructor.id)
                            }
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() => {
                          setSelectedInstructor(instructor)
                          setDeleteConfirmOpen(true)
                        }}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>

                  {instructor.certifications && (
                    <div>
                      <h4 className="text-sm font-medium text-dark-300 mb-1">Certyfikaty:</h4>
                      <p className="text-dark-200 text-sm whitespace-pre-line">
                        {instructor.certifications}
                      </p>
                    </div>
                  )}

                  {instructor.bio && (
                    <div>
                      <h4 className="text-sm font-medium text-dark-300 mb-1">Bio:</h4>
                      <p className="text-dark-200 text-sm whitespace-pre-line">{instructor.bio}</p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Dodaj Instruktora"
      >
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">
              Imię *
            </label>
            <input
              type="text"
              name="firstName"
              required
              maxLength={100}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">
              Nazwisko *
            </label>
            <input
              type="text"
              name="lastName"
              required
              maxLength={100}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">
              Certyfikaty
            </label>
            <textarea
              name="certifications"
              rows={3}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-sm font-medium text-dark-200">Bio</label>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => { setPickerTarget('create'); setShowMediaPicker(true) }}
              >
                <Library className="w-4 h-4 mr-1.5" />
                Wstaw obraz z biblioteki
              </Button>
            </div>
            <textarea
              ref={createBioRef}
              name="bio"
              rows={4}
              value={createBio}
              onChange={(e) => setCreateBio(e.target.value)}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div className="flex gap-2 justify-end">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setCreateModalOpen(false)}
            >
              Anuluj
            </Button>
            <Button type="submit" loading={createMutation.isPending}>
              Dodaj
            </Button>
          </div>

          {createMutation.error && (
            <div className="text-rose-400 text-sm">{String(createMutation.error)}</div>
          )}
        </form>
      </Modal>

      {/* Edit Modal */}
      {selectedInstructor && (
        <Modal
          isOpen={editModalOpen}
          onClose={() => {
            setEditModalOpen(false)
            setSelectedInstructor(null)
          }}
          title="Edytuj Instruktora"
        >
          <form onSubmit={handleUpdate} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Imię *
              </label>
              <input
                type="text"
                name="firstName"
                defaultValue={selectedInstructor.firstName}
                required
                maxLength={100}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Nazwisko *
              </label>
              <input
                type="text"
                name="lastName"
                defaultValue={selectedInstructor.lastName}
                required
                maxLength={100}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Certyfikaty
              </label>
              <textarea
                name="certifications"
                defaultValue={selectedInstructor.certifications || ''}
                rows={3}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-sm font-medium text-dark-200">Bio</label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => { setPickerTarget('edit'); setShowMediaPicker(true) }}
                >
                  <Library className="w-4 h-4 mr-1.5" />
                  Wstaw obraz z biblioteki
                </Button>
              </div>
              <textarea
                ref={editBioRef}
                name="bio"
                rows={4}
                value={editBio}
                onChange={(e) => setEditBio(e.target.value)}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            {selectedInstructor.photoUrl && (
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="block text-sm font-medium text-dark-200">
                    Kadrowanie zdjęcia
                  </label>
                  <button
                    type="button"
                    onClick={() => setFocalPoint({ x: 0.5, y: 0.5 })}
                    className="text-xs text-primary-400 hover:text-primary-300 transition-colors"
                  >
                    Resetuj
                  </button>
                </div>
                <FocalPointEditor
                  imageUrl={selectedInstructor.photoUrl}
                  value={focalPoint}
                  onChange={setFocalPoint}
                  aspectRatio="1/1"
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-dark-200 mb-1">
                  Kolejność
                </label>
                <input
                  type="number"
                  name="displayOrder"
                  defaultValue={selectedInstructor.displayOrder}
                  min="0"
                  className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                />
              </div>

              <div className="flex items-end">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    name="active"
                    defaultChecked={selectedInstructor.active}
                    className="w-4 h-4 rounded border-dark-600 bg-dark-700 text-primary-500 focus:ring-2 focus:ring-primary-500"
                  />
                  <span className="text-sm text-dark-200">Aktywny</span>
                </label>
              </div>
            </div>

            <div className="flex gap-2 justify-end">
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setEditModalOpen(false)
                  setSelectedInstructor(null)
                }}
              >
                Anuluj
              </Button>
              <Button type="submit" loading={updateMutation.isPending}>
                Zapisz
              </Button>
            </div>

            {updateMutation.error && (
              <div className="text-rose-400 text-sm">{String(updateMutation.error)}</div>
            )}
          </form>
        </Modal>
      )}

      {/* Upload Photo Modal */}
      {selectedInstructor && (
        <Modal
          isOpen={uploadPhotoModalOpen}
          onClose={() => {
            setUploadPhotoModalOpen(false)
            setSelectedInstructor(null)
            setPhotoPreview(null)
            setSelectedFile(null)
          }}
          title="Prześlij Zdjęcie"
        >
          <div className="space-y-4">
            <FileUpload
              onFileSelect={handleFileSelect}
              onClear={() => {
                setPhotoPreview(null)
                setSelectedFile(null)
              }}
              previews={photoPreview ? [photoPreview] : []}
            />

            <div className="flex gap-2 justify-end">
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setUploadPhotoModalOpen(false)
                  setSelectedInstructor(null)
                  setPhotoPreview(null)
                  setSelectedFile(null)
                }}
              >
                Anuluj
              </Button>
              <Button
                onClick={handleUploadPhoto}
                disabled={!selectedFile}
                loading={uploadPhotoMutation.isPending}
              >
                Prześlij
              </Button>
            </div>

            {uploadPhotoMutation.error && (
              <div className="text-rose-400 text-sm">{String(uploadPhotoMutation.error)}</div>
            )}
          </div>
        </Modal>
      )}

      {/* Media Picker */}
      <MediaPickerModal
        isOpen={showMediaPicker}
        onClose={() => setShowMediaPicker(false)}
        onSelect={(asset) => {
          if (pickerTarget === 'badge' && badgeTargetId) {
            setBadgeMutation.mutate({ id: badgeTargetId, badgeUrl: asset.url })
          } else {
            insertImageAtCursor(asset.url, pickerTarget as 'create' | 'edit')
          }
          setShowMediaPicker(false)
        }}
      />

      {/* Delete Confirm */}
      <ConfirmModal
        isOpen={deleteConfirmOpen}
        onClose={() => {
          setDeleteConfirmOpen(false)
          setSelectedInstructor(null)
        }}
        onConfirm={() => {
          if (selectedInstructor) {
            deleteMutation.mutate(selectedInstructor.id)
          }
        }}
        title="Usuń Instruktora"
        message={`Czy na pewno chcesz usunąć instruktora ${selectedInstructor?.firstName} ${selectedInstructor?.lastName}? Tej operacji nie można cofnąć.`}
        confirmText="Usuń"
      />
    </div>
  )
}
