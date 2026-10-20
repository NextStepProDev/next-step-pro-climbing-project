import { useState, useEffect, useCallback, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useFocusTrap } from '../../utils/useFocusTrap'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Image as ImageIcon, Plus, Pencil, Trash2, Upload, ChevronUp, ChevronDown, Star, Eye, EyeOff, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { adminGalleryApi } from '../../api/client'
import type { AlbumAdmin, CreateAlbumRequest, UpdateAlbumRequest, UpdatePhotoRequest } from '../../types'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ModalCancelButton } from '../../components/ui/ModalCancelButton'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { FileUpload } from '../../components/ui/FileUpload'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import { useBodyScrollLock } from '../../components/ui/scrollLock'
import { getErrorMessage } from '../../utils/errors'

export function AdminGalleryPanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [localOrder, setLocalOrder] = useState<AlbumAdmin[] | null>(null)
  const [createAlbumModalOpen, setCreateAlbumModalOpen] = useState(false)
  const [editAlbumModalOpen, setEditAlbumModalOpen] = useState(false)
  const [uploadPhotoModalOpen, setUploadPhotoModalOpen] = useState(false)
  const [editPhotoModalOpen, setEditPhotoModalOpen] = useState(false)
  const [deleteAlbumConfirmOpen, setDeleteAlbumConfirmOpen] = useState(false)
  const [deletePhotoConfirmOpen, setDeletePhotoConfirmOpen] = useState(false)
  const [deleteAllPhotosConfirmOpen, setDeleteAllPhotosConfirmOpen] = useState(false)
  const [selectedAlbum, setSelectedAlbum] = useState<AlbumAdmin | null>(null)
  const [selectedPhotoId, setSelectedPhotoId] = useState<string | null>(null)
  const [selectedPhoto, setSelectedPhoto] = useState<{ id: string; caption: string | null; url: string; focalPointX: number | null; focalPointY: number | null } | null>(null)
  const [focalPoint, setFocalPoint] = useState({ x: 0.5, y: 0.5 })
  const [expandedAlbumId, setExpandedAlbumId] = useState<string | null>(null)
  const [photoPreviews, setPhotoPreviews] = useState<string[]>([])
  const [selectedFiles, setSelectedFiles] = useState<File[]>([])
  const [uploadProgress, setUploadProgress] = useState<number>(0)
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)
  const lightboxTrapRef = useFocusTrap(lightboxIndex !== null)
  useBodyScrollLock(lightboxIndex !== null)
  const [uploadError, setUploadError] = useState<string | null>(null)

  // Controlled edit fields so the "Zapisz" dirty state can be derived during render.
  const [editAlbumName, setEditAlbumName] = useState('')
  const [editAlbumDescription, setEditAlbumDescription] = useState('')
  const [editPhotoCaption, setEditPhotoCaption] = useState('')

  const createAlbumFormRef = useRef<HTMLFormElement>(null)
  const albumDirty = selectedAlbum != null && (
    editAlbumName !== selectedAlbum.name ||
    editAlbumDescription !== (selectedAlbum.description || '')
  )

  const photoDirty = selectedPhoto != null && (
    editPhotoCaption !== (selectedPhoto.caption || '') ||
    focalPoint.x !== (selectedPhoto.focalPointX ?? 0.5) ||
    focalPoint.y !== (selectedPhoto.focalPointY ?? 0.5)
  )

  const { data: albums, isLoading, error } = useQuery({
    queryKey: ['admin', 'gallery', 'albums'],
    queryFn: adminGalleryApi.getAllAlbums,
  })

  const orderedAlbums = localOrder ?? (albums ? [...albums].sort((a, b) => a.displayOrder - b.displayOrder) : [])

  const { data: albumDetail } = useQuery({
    queryKey: ['admin', 'gallery', 'album', expandedAlbumId],
    queryFn: () => adminGalleryApi.getAlbum(expandedAlbumId!),
    enabled: !!expandedAlbumId,
    // Detail-by-id: expanding a different album must not flash the previously
    // expanded album's photos. Opt out of the global keepPreviousData.
    placeholderData: undefined,
  })

  const createAlbumMutation = useMutation({
    mutationFn: adminGalleryApi.createAlbum,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery', 'albums'] })
      setCreateAlbumModalOpen(false)
    },
  })

  const updateAlbumMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateAlbumRequest }) =>
      adminGalleryApi.updateAlbum(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
      setEditAlbumModalOpen(false)
      setSelectedAlbum(null)
    },
  })

  const deleteAlbumMutation = useMutation({
    mutationFn: adminGalleryApi.deleteAlbum,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
      setDeleteAlbumConfirmOpen(false)
      setSelectedAlbum(null)
      setExpandedAlbumId(null)
    },
  })

  const uploadPhotoMutation = useMutation({
    mutationFn: ({ albumId, file, caption }: { albumId: string; file: File; caption?: string }) =>
      adminGalleryApi.uploadPhoto(albumId, file, caption),
  })

  const deletePhotoMutation = useMutation({
    mutationFn: adminGalleryApi.deletePhoto,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
      setDeletePhotoConfirmOpen(false)
      setSelectedPhotoId(null)
    },
  })

  const deleteAllPhotosMutation = useMutation({
    mutationFn: adminGalleryApi.deleteAllPhotos,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
      setDeleteAllPhotosConfirmOpen(false)
    },
  })

  const updatePhotoMutation = useMutation({
    mutationFn: ({ photoId, data }: { photoId: string; data: UpdatePhotoRequest }) =>
      adminGalleryApi.updatePhoto(photoId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
      setEditPhotoModalOpen(false)
      setSelectedPhoto(null)
    },
  })

  const setThumbnailMutation = useMutation({
    mutationFn: ({ albumId, photoId }: { albumId: string; photoId: string }) =>
      adminGalleryApi.setThumbnailPhoto(albumId, { photoId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
    },
  })

  const reorderMutation = useMutation({
    mutationFn: (orderedIds: string[]) =>
      adminGalleryApi.reorderAlbums({ orderedIds }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'gallery', 'albums'] })
      setLocalOrder(null)
    },
    // The swapped order is drawn before the server answers; a refusal has to put it back, or the
    // list keeps showing an order nobody saved.
    onError: () => setLocalOrder(null),
  })

  const publishAlbumMutation = useMutation({
    mutationFn: adminGalleryApi.publishAlbum,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery', 'albums'] })
    },
  })

  const unpublishAlbumMutation = useMutation({
    mutationFn: adminGalleryApi.unpublishAlbum,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'gallery', 'albums'] })
    },
  })

  const handleMove = (index: number, direction: 'up' | 'down') => {
    const newOrder = [...orderedAlbums]
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= newOrder.length) return
    ;[newOrder[index], newOrder[targetIndex]] = [newOrder[targetIndex], newOrder[index]]
    setLocalOrder(newOrder)
    reorderMutation.mutate(newOrder.map(a => a.id))
  }

  const handleCreateAlbum = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const formData = new FormData(e.currentTarget)
    const data: CreateAlbumRequest = {
      name: formData.get('name') as string,
      description: formData.get('description') as string || undefined,
    }
    createAlbumMutation.mutate(data)
  }

  const handleUpdateAlbum = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedAlbum) return
    const data: UpdateAlbumRequest = {
      name: editAlbumName,
      // "" clears it; undefined would mean "leave it" to the server
      description: editAlbumDescription,
    }
    updateAlbumMutation.mutate({ id: selectedAlbum.id, data })
  }

  const handleUploadPhoto = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedAlbum || selectedFiles.length === 0) return

    const formData = new FormData(e.currentTarget)
    const caption = formData.get('caption') as string

    setUploadProgress(0)
    setUploadError(null)

    // Sequentially, one request per photo. A failure part-way used to close the dialog and drop
    // the whole selection, so the admin had to work out which photos had not made it and pick
    // them again. Now the uploaded ones leave the selection and the rest stay, with the reason.
    let uploaded = 0
    for (let i = 0; i < selectedFiles.length; i++) {
      try {
        await uploadPhotoMutation.mutateAsync({
          albumId: selectedAlbum.id,
          file: selectedFiles[i],
          caption: selectedFiles.length === 1 ? caption : undefined,
        })
        uploaded = i + 1
        setUploadProgress(Math.round(((i + 1) / selectedFiles.length) * 100))
      } catch (error) {
        setUploadError(getErrorMessage(error))
        break
      }
    }

    queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
    setUploadProgress(0)
    if (uploaded < selectedFiles.length) {
      setSelectedFiles(selectedFiles.slice(uploaded))
      setPhotoPreviews(photoPreviews.slice(uploaded))
      return
    }
    setUploadPhotoModalOpen(false)
    setPhotoPreviews([])
    setSelectedFiles([])
  }

  const handleFileSelect = (files: File[]) => {
    setSelectedFiles(files)

    // Use createObjectURL instead of FileReader for memory efficiency
    // This creates lightweight blob URLs without loading files into memory
    const previews = files.map(file => URL.createObjectURL(file))
    setPhotoPreviews(previews)
  }

  // Cleanup: revoke blob URLs when component unmounts or previews change
  useEffect(() => {
    return () => {
      photoPreviews.forEach(url => {
        URL.revokeObjectURL(url)
      })
    }
  }, [photoPreviews])

  const lightboxPhotos = albumDetail?.photos ?? []

  const handleLightboxPrev = useCallback(() => {
    setLightboxIndex(prev => prev !== null && prev > 0 ? prev - 1 : prev)
  }, [])

  const handleLightboxNext = useCallback(() => {
    setLightboxIndex(prev => prev !== null && prev < lightboxPhotos.length - 1 ? prev + 1 : prev)
  }, [lightboxPhotos.length])

  const touchStartX = useRef<number | null>(null)

  useEffect(() => {
    if (lightboxIndex === null) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setLightboxIndex(null)
      else if (e.key === 'ArrowLeft') handleLightboxPrev()
      else if (e.key === 'ArrowRight') handleLightboxNext()
    }
    const handleTouchStart = (e: TouchEvent) => {
      touchStartX.current = e.touches[0].clientX
    }
    const handleTouchEnd = (e: TouchEvent) => {
      if (touchStartX.current === null) return
      const diff = touchStartX.current - e.changedTouches[0].clientX
      touchStartX.current = null
      if (Math.abs(diff) < 50) return
      if (diff > 0) handleLightboxNext()
      else handleLightboxPrev()
    }
    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('touchstart', handleTouchStart)
    document.addEventListener('touchend', handleTouchEnd)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('touchstart', handleTouchStart)
      document.removeEventListener('touchend', handleTouchEnd)
    }
  }, [lightboxIndex, handleLightboxPrev, handleLightboxNext])

  const handleUpdatePhoto = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedPhoto) return
    updatePhotoMutation.mutate({
      photoId: selectedPhoto.id,
      data: {
        caption: editPhotoCaption,
        focalPointX: focalPoint.x,
        focalPointY: focalPoint.y,
      },
    })
  }

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
        <h2 className="text-2xl font-bold text-surface-100">{t('galleryAdmin.title')}</h2>
        <Button onClick={() => setCreateAlbumModalOpen(true)}>
          <Plus className="h-4 w-4 mr-2" />
          {t('galleryAdmin.newAlbum')}
        </Button>
      </div>

      {/* Albums List */}
      {orderedAlbums.length === 0 && !isLoading ? (
        <div className="text-center py-12 text-surface-400">
          {t('galleryAdmin.noAlbums')}
        </div>
      ) : (
        <div className="space-y-4">
          {orderedAlbums.map((album, index) => (
            <div
              key={album.id}
              className="bg-surface-800 rounded-lg border border-surface-700 overflow-hidden"
            >
              {/* Album Header */}
              <div className="p-4 sm:p-6">
                <div className="flex flex-col sm:flex-row gap-4 sm:gap-6">
                  {/* Thumbnail */}
                  <div className="flex-shrink-0 w-full sm:w-auto">
                    {album.thumbnailUrl ? (
                      <div className="w-full h-40 sm:w-32 sm:h-24 rounded-lg overflow-hidden relative bg-surface-700">
                        <div
                          className="absolute inset-0 scale-110 blur-xl"
                          style={{
                            backgroundImage: `url(${album.thumbnailUrl})`,
                            backgroundSize: 'cover',
                            backgroundPosition: album.thumbnailFocalPointX != null
                              ? `${album.thumbnailFocalPointX * 100}% ${(album.thumbnailFocalPointY ?? 0.5) * 100}%`
                              : 'center',
                          }}
                        />
                        <img
                          src={album.thumbnailUrl}
                          alt={album.name}
                          className="relative w-full h-full object-contain"
                        />
                      </div>
                    ) : (
                      <div className="w-full h-40 sm:w-32 sm:h-24 bg-surface-700 rounded-lg flex items-center justify-center">
                        <ImageIcon className="h-8 w-8 text-surface-500" />
                      </div>
                    )}
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex flex-col gap-3">
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="text-xl font-bold text-surface-100">{album.name}</h3>
                          {album.published ? (
                            <span className="flex items-center gap-1 text-xs text-green-400 font-medium">
                              <Eye className="h-3 w-3" />
                              {t('galleryAdmin.published')}
                            </span>
                          ) : (
                            <span className="flex items-center gap-1 text-xs text-surface-400 font-medium bg-surface-700 px-2 py-0.5 rounded-full">
                              <EyeOff className="h-3 w-3" />
                              {t('galleryAdmin.draft')}
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-surface-400 mt-1">
                          {t('galleryAdmin.photoCount', { count: album.photoCount })}
                        </p>
                        {album.description && (
                          <p className="text-surface-300 mt-2 text-sm">{album.description}</p>
                        )}
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleMove(index, 'up')}
                          disabled={index === 0 || reorderMutation.isPending}
                          title={t('galleryAdmin.moveUp')}
                        >
                          <ChevronUp className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleMove(index, 'down')}
                          disabled={index === orderedAlbums.length - 1 || reorderMutation.isPending}
                          title={t('galleryAdmin.moveDown')}
                        >
                          <ChevronDown className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setExpandedAlbumId(expandedAlbumId === album.id ? null : album.id)
                          }}
                        >
                          {expandedAlbumId === album.id ? t('galleryAdmin.collapse') : t('galleryAdmin.expand')}
                        </Button>
                        {album.published ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => unpublishAlbumMutation.mutate(album.id)}
                            disabled={unpublishAlbumMutation.isPending}
                            title={t('galleryAdmin.unpublish')}
                            className="text-green-400 hover:text-orange-400"
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => publishAlbumMutation.mutate(album.id)}
                            disabled={publishAlbumMutation.isPending}
                            title={t('galleryAdmin.publish')}
                          >
                            <EyeOff className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setSelectedAlbum(album)
                            setEditAlbumName(album.name)
                            setEditAlbumDescription(album.description || '')
                            setEditAlbumModalOpen(true)
                          }}
                          title={t('galleryAdmin.editTitle')}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => {
                            setSelectedAlbum(album)
                            setDeleteAlbumConfirmOpen(true)
                          }}
                          title={t('galleryAdmin.deleteAlbumTitle')}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Expanded Photos */}
              {expandedAlbumId === album.id && albumDetail && (
                <div className="border-t border-surface-700 p-6 bg-surface-900/50">
                  <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
                    <h4 className="font-semibold text-surface-100">{t('galleryAdmin.photosInAlbum')}</h4>
                    <div className="flex gap-2">
                      {albumDetail.photos.length > 0 && (
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => {
                            setSelectedAlbum(album)
                            setDeleteAllPhotosConfirmOpen(true)
                          }}
                        >
                          <Trash2 className="h-4 w-4 mr-2" />
                          {t('galleryAdmin.deleteAll')}
                        </Button>
                      )}
                      <Button
                        size="sm"
                        onClick={() => {
                          setSelectedAlbum(album)
                          setUploadError(null)
                          setUploadPhotoModalOpen(true)
                        }}
                      >
                        <Upload className="h-4 w-4 mr-2" />
                        {t('galleryAdmin.addPhoto')}
                      </Button>
                    </div>
                  </div>

                  {albumDetail.photos.length === 0 ? (
                    <div className="text-center py-8 text-surface-400 text-sm">
                      {t('galleryAdmin.noPhotos')}
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                      {albumDetail.photos.map((photo) => (
                        <div
                          key={photo.id}
                          className="group relative bg-surface-700 rounded-lg overflow-hidden aspect-square cursor-pointer"
                          onClick={() => {
                            const idx = albumDetail!.photos.findIndex(p => p.id === photo.id)
                            setLightboxIndex(idx >= 0 ? idx : 0)
                          }}
                        >
                          <div
                            className="absolute inset-0 scale-110 blur-xl"
                            style={{
                              backgroundImage: `url(${photo.url})`,
                              backgroundSize: 'cover',
                              backgroundPosition: 'center',
                            }}
                          />
                          <img
                            src={photo.url}
                            alt={photo.caption || ''}
                            className="relative w-full h-full object-contain"
                          />
                          {photo.caption && (
                            <div className="p-2 text-xs text-surface-300 line-clamp-2">
                              {photo.caption}
                            </div>
                          )}
                          {/* Thumbnail badge — always visible */}
                          {albumDetail.thumbnailPhotoId === photo.id && (
                            <div
                              className="absolute top-2 left-2 bg-amber-400 rounded-full p-1 shadow-md"
                              title={t('galleryAdmin.albumThumbnail')}
                            >
                              <Star className="h-3 w-3 text-surface-900" fill="currentColor" />
                            </div>
                          )}
                          {/* Hover on a mouse; always shown on touch and under keyboard focus (see AdminAssetsPanel) */}
                          <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 focus-within:opacity-100 pointer-coarse:opacity-100 pointer-coarse:bg-black/30 transition-opacity flex items-center justify-center gap-2">
                            {/* Set as thumbnail */}
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation()
                                if (expandedAlbumId) {
                                  setThumbnailMutation.mutate({ albumId: expandedAlbumId, photoId: photo.id })
                                }
                              }}
                              disabled={setThumbnailMutation.isPending}
                              title={albumDetail.thumbnailPhotoId === photo.id ? t('galleryAdmin.currentThumbnail') : t('galleryAdmin.setThumbnail')}
                              className={`bg-surface-800/80 hover:bg-surface-700${albumDetail.thumbnailPhotoId === photo.id ? ' text-amber-400' : ''}`}
                            >
                              <Star
                                className="h-4 w-4"
                                fill={albumDetail.thumbnailPhotoId === photo.id ? 'currentColor' : 'none'}
                              />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation()
                                setSelectedPhoto({ id: photo.id, caption: photo.caption, url: photo.url, focalPointX: photo.focalPointX, focalPointY: photo.focalPointY })
                                setEditPhotoCaption(photo.caption || '')
                                setFocalPoint({ x: photo.focalPointX ?? 0.5, y: photo.focalPointY ?? 0.5 })
                                setEditPhotoModalOpen(true)
                              }}
                              title={t('galleryAdmin.editPhotoTitle')}
                              className="bg-surface-800/80 hover:bg-surface-700"
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation()
                                setSelectedPhotoId(photo.id)
                                setDeletePhotoConfirmOpen(true)
                              }}
                              title={t('galleryAdmin.deletePhoto')}
                              className="bg-surface-800/80 hover:bg-surface-700"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                          {photo.caption && (
                            <div className="absolute bottom-0 left-0 right-0 bg-black/80 text-white text-xs p-2 rounded-b-lg">
                              {photo.caption}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create Album Modal */}
      <Modal
        isOpen={createAlbumModalOpen}
        onClose={() => setCreateAlbumModalOpen(false)}
        title={t('galleryAdmin.createTitle')}
        // The fields are uncontrolled, so the question reads them at the moment of closing.
        confirmClose={() => {
          const form = createAlbumFormRef.current
          return !!form && [...new FormData(form).values()].some((v) => String(v).trim() !== '')
        }}
      >
        <form ref={createAlbumFormRef} onSubmit={handleCreateAlbum} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-200 mb-1">
              {t('galleryAdmin.albumName')}
            </label>
            <input
              type="text"
              name="name"
              required
              maxLength={255}
              className="w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-surface-200 mb-1">
              {t('galleryAdmin.description')}
            </label>
            <textarea
              name="description"
              rows={8}
              className="w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div className="flex gap-2 justify-end">
            <ModalCancelButton onClose={() => setCreateAlbumModalOpen(false)}>{t('galleryAdmin.cancel')}</ModalCancelButton>
            <Button type="submit" loading={createAlbumMutation.isPending}>
              {t('galleryAdmin.create')}
            </Button>
          </div>

          {createAlbumMutation.error && (
            <div className="text-rose-400 text-sm">{getErrorMessage(createAlbumMutation.error)}</div>
          )}
        </form>
      </Modal>

      {/* Edit Album Modal */}
      {selectedAlbum && (
        <Modal
          isOpen={editAlbumModalOpen}
          onClose={() => {
            setEditAlbumModalOpen(false)
            setSelectedAlbum(null)
          }}
          title={t('galleryAdmin.editTitle')}
          confirmClose={() => albumDirty}
        >
          <form onSubmit={handleUpdateAlbum} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">
                {t('galleryAdmin.albumName')}
              </label>
              <input
                type="text"
                name="name"
                value={editAlbumName}
                onChange={(e) => setEditAlbumName(e.target.value)}
                required
                maxLength={255}
                className="w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">
                {t('galleryAdmin.description')}
              </label>
              <textarea
                name="description"
                value={editAlbumDescription}
                onChange={(e) => setEditAlbumDescription(e.target.value)}
                rows={8}
                className="w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div className="flex gap-2 justify-end">
              <ModalCancelButton
                onClose={() => {
                  setEditAlbumModalOpen(false)
                  setSelectedAlbum(null)
                }}
              >
                {t('galleryAdmin.cancel')}
              </ModalCancelButton>
              <Button type="submit" loading={updateAlbumMutation.isPending} disabled={!albumDirty}>
                {t('galleryAdmin.save')}
              </Button>
            </div>

            {updateAlbumMutation.error && (
              <div className="text-rose-400 text-sm">{getErrorMessage(updateAlbumMutation.error)}</div>
            )}
          </form>
        </Modal>
      )}

      {/* Upload Photo Modal */}
      {selectedAlbum && (
        <Modal
          isOpen={uploadPhotoModalOpen}
          // Photos already picked (and optimised in the browser) would be lost to a stray tap.
          confirmClose={() => selectedFiles.length > 0 && !uploadPhotoMutation.isPending}
          onClose={() => {
            setUploadPhotoModalOpen(false)
            setSelectedAlbum(null)
            setPhotoPreviews([])
            setSelectedFiles([])
            setUploadProgress(0)
          }}
          title={t('galleryAdmin.uploadTitle')}
        >
          <form onSubmit={handleUploadPhoto} className="space-y-4">
            <FileUpload
              onFileSelect={handleFileSelect}
              onClear={() => {
                setPhotoPreviews([])
                setSelectedFiles([])
              }}
              multiple
              previews={photoPreviews}
            />

            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">
                {t('galleryAdmin.uploadCaption')}
              </label>
              <input
                type="text"
                name="caption"
                disabled={selectedFiles.length > 1}
                className="w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:opacity-50"
              />
            </div>

            {uploadProgress > 0 && uploadProgress < 100 && (
              <div className="space-y-2">
                <div className="flex justify-between text-sm text-surface-300">
                  <span>{t('galleryAdmin.uploading')}</span>
                  <span>{uploadProgress}%</span>
                </div>
                <div className="w-full bg-surface-700 rounded-full h-2 overflow-hidden">
                  <div
                    className="bg-primary-500 h-full transition-all duration-300"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
              </div>
            )}

            <div className="flex gap-2 justify-end">
              <ModalCancelButton
                onClose={() => {
                  setUploadPhotoModalOpen(false)
                  setSelectedAlbum(null)
                  setPhotoPreviews([])
                  setSelectedFiles([])
                  setUploadProgress(0)
                }}
                disabled={uploadPhotoMutation.isPending}
              >
                {t('galleryAdmin.cancel')}
              </ModalCancelButton>
              <Button
                type="submit"
                disabled={selectedFiles.length === 0 || uploadPhotoMutation.isPending}
                loading={uploadPhotoMutation.isPending}
              >
                {t('galleryAdmin.upload')} {selectedFiles.length > 1 ? `(${selectedFiles.length})` : ''}
              </Button>
            </div>

            {uploadError && (
              <div className="text-rose-400 text-sm">{uploadError}</div>
            )}
          </form>
        </Modal>
      )}

      {/* Edit Photo Modal */}
      {selectedPhoto && (
        <Modal
          isOpen={editPhotoModalOpen}
          onClose={() => {
            setEditPhotoModalOpen(false)
            setSelectedPhoto(null)
          }}
          title={t('galleryAdmin.editPhotoTitle')}
          confirmClose={() => photoDirty}
        >
          <form onSubmit={handleUpdatePhoto} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-surface-200 mb-1">
                {t('galleryAdmin.caption')}
              </label>
              <textarea
                name="caption"
                value={editPhotoCaption}
                onChange={(e) => setEditPhotoCaption(e.target.value)}
                rows={2}
                className="w-full px-3 py-2 bg-surface-700 border border-surface-600 rounded-lg text-surface-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                placeholder={t('galleryAdmin.captionPlaceholder')}
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-surface-200">
                  {t('galleryAdmin.crop')}
                </label>
                <button
                  type="button"
                  onClick={() => setFocalPoint({ x: 0.5, y: 0.5 })}
                  className="text-xs text-primary-400 hover:text-primary-300 transition-colors"
                >
                  {t('galleryAdmin.reset')}
                </button>
              </div>
              <FocalPointEditor
                imageUrl={selectedPhoto.url}
                value={focalPoint}
                onChange={setFocalPoint}
                aspectRatio="1/1"
              />
            </div>

            <button
              type="button"
              onClick={() => {
                setEditPhotoModalOpen(false)
                if (selectedPhoto) setSelectedPhotoId(selectedPhoto.id)
                setDeletePhotoConfirmOpen(true)
              }}
              className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-red-600/20 hover:bg-red-600/40 text-red-400 rounded-lg transition-colors text-sm"
            >
              <Trash2 className="w-4 h-4" />
              {t('galleryAdmin.deletePhoto')}
            </button>

            <div className="flex gap-2 justify-end">
              <ModalCancelButton
                onClose={() => {
                  setEditPhotoModalOpen(false)
                  setSelectedPhoto(null)
                }}
              >
                {t('galleryAdmin.cancel')}
              </ModalCancelButton>
              <Button
                type="submit"
                loading={updatePhotoMutation.isPending}
                disabled={!photoDirty}
              >
                {t('galleryAdmin.save')}
              </Button>
            </div>

            {updatePhotoMutation.error && (
              <div className="text-rose-400 text-sm">{getErrorMessage(updatePhotoMutation.error)}</div>
            )}
          </form>
        </Modal>
      )}

      {/* Delete Album Confirm */}
      <ConfirmModal
        isOpen={deleteAlbumConfirmOpen}
        onClose={() => {
          setDeleteAlbumConfirmOpen(false)
          setSelectedAlbum(null)
        }}
        onConfirm={() => {
          if (selectedAlbum) {
            deleteAlbumMutation.mutate(selectedAlbum.id)
          }
        }}
        title={t('galleryAdmin.deleteAlbumTitle')}
        message={t('galleryAdmin.deleteAlbumMessage', { name: selectedAlbum?.name ?? '' })}
        confirmText={t('galleryAdmin.deleteAlbumConfirm')}
      />

      {/* Delete Photo Confirm */}
      <ConfirmModal
        isOpen={deletePhotoConfirmOpen}
        onClose={() => {
          setDeletePhotoConfirmOpen(false)
          setSelectedPhotoId(null)
        }}
        onConfirm={() => {
          if (selectedPhotoId) {
            deletePhotoMutation.mutate(selectedPhotoId)
          }
        }}
        title={t('galleryAdmin.deletePhotoTitle')}
        message={t('galleryAdmin.deletePhotoMessage')}
        confirmText={t('galleryAdmin.deletePhotoConfirm')}
      />

      {/* Delete All Photos Confirm */}
      <ConfirmModal
        isOpen={deleteAllPhotosConfirmOpen}
        onClose={() => {
          setDeleteAllPhotosConfirmOpen(false)
          setSelectedAlbum(null)
        }}
        onConfirm={() => {
          if (selectedAlbum) {
            deleteAllPhotosMutation.mutate(selectedAlbum.id)
          }
        }}
        title={t('galleryAdmin.deleteAllTitle')}
        message={t('galleryAdmin.deleteAllMessage', { name: selectedAlbum?.name ?? '' })}
        confirmText={t('galleryAdmin.deleteAllConfirm')}
      />

      {/* Photo Lightbox */}
      {lightboxIndex !== null && lightboxPhotos[lightboxIndex] && (
        <div
          ref={lightboxTrapRef}
          role="dialog"
          aria-modal="true"
          aria-label={t('galleryAdmin.lightbox')}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/90"
          onClick={() => setLightboxIndex(null)}
        >
          <button
            aria-label={t('galleryAdmin.close')}
            onClick={(e) => { e.stopPropagation(); setLightboxIndex(null) }}
            className="absolute top-4 right-4 text-white/70 hover:text-white transition-colors z-10"
          >
            <X className="h-8 w-8" />
          </button>

          <div className="absolute top-4 left-1/2 -translate-x-1/2 text-white/50 text-sm">
            {lightboxIndex + 1} / {lightboxPhotos.length}
          </div>

          {lightboxIndex > 0 && (
            <button
              aria-label={t('galleryAdmin.previous')}
              onClick={(e) => { e.stopPropagation(); handleLightboxPrev() }}
              className="absolute left-4 top-1/2 -translate-y-1/2 text-white/50 hover:text-white transition-colors z-10"
            >
              <ChevronLeft className="h-10 w-10" />
            </button>
          )}

          {lightboxIndex < lightboxPhotos.length - 1 && (
            <button
              aria-label={t('galleryAdmin.next')}
              onClick={(e) => { e.stopPropagation(); handleLightboxNext() }}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-white/50 hover:text-white transition-colors z-10"
            >
              <ChevronRight className="h-10 w-10" />
            </button>
          )}

          <img
            src={lightboxPhotos[lightboxIndex].url}
            alt={lightboxPhotos[lightboxIndex].caption || ''}
            className="max-h-[90vh] max-w-[90vw] object-contain"
            onClick={(e) => e.stopPropagation()}
          />

          <div className="absolute bottom-6 left-1/2 -translate-x-1/2 flex flex-col items-center gap-3" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  if (expandedAlbumId) {
                    setThumbnailMutation.mutate({ albumId: expandedAlbumId, photoId: lightboxPhotos[lightboxIndex].id })
                  }
                }}
                disabled={setThumbnailMutation.isPending}
                title={albumDetail?.thumbnailPhotoId === lightboxPhotos[lightboxIndex].id ? t('galleryAdmin.currentThumbnail') : t('galleryAdmin.setThumbnail')}
                className={`bg-surface-800/80 hover:bg-surface-700 text-white${albumDetail?.thumbnailPhotoId === lightboxPhotos[lightboxIndex].id ? ' !text-amber-400' : ''}`}
              >
                <Star
                  className="h-4 w-4"
                  fill={albumDetail?.thumbnailPhotoId === lightboxPhotos[lightboxIndex].id ? 'currentColor' : 'none'}
                />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  const photo = lightboxPhotos[lightboxIndex]
                  setSelectedPhoto({ id: photo.id, caption: photo.caption, url: photo.url, focalPointX: photo.focalPointX, focalPointY: photo.focalPointY })
                  setEditPhotoCaption(photo.caption || '')
                  setFocalPoint({ x: photo.focalPointX ?? 0.5, y: photo.focalPointY ?? 0.5 })
                  setLightboxIndex(null)
                  setEditPhotoModalOpen(true)
                }}
                title={t('galleryAdmin.editPhotoTitle')}
                className="bg-surface-800/80 hover:bg-surface-700 text-white"
              >
                <Pencil className="h-4 w-4" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setSelectedPhotoId(lightboxPhotos[lightboxIndex].id)
                  setLightboxIndex(null)
                  setDeletePhotoConfirmOpen(true)
                }}
                title={t('galleryAdmin.deletePhoto')}
                className="bg-surface-800/80 hover:bg-surface-700 text-white"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
            {lightboxPhotos[lightboxIndex].caption && (
              <div className="text-white/80 text-sm bg-black/60 px-4 py-2 rounded-lg max-w-lg text-center">
                {lightboxPhotos[lightboxIndex].caption}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

