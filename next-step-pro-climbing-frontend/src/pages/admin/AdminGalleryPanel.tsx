import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Image as ImageIcon, Plus, Pencil, Trash2, Upload, ChevronUp, ChevronDown, Star } from 'lucide-react'
import { adminGalleryApi } from '../../api/client'
import type { AlbumAdmin, CreateAlbumRequest, UpdateAlbumRequest, UpdatePhotoRequest } from '../../types'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { FileUpload } from '../../components/ui/FileUpload'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'

export function AdminGalleryPanel() {
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

  const { data: albums, isLoading, error } = useQuery({
    queryKey: ['admin', 'gallery', 'albums'],
    queryFn: adminGalleryApi.getAllAlbums,
  })

  const orderedAlbums = localOrder ?? (albums ? [...albums].sort((a, b) => a.displayOrder - b.displayOrder) : [])

  const { data: albumDetail } = useQuery({
    queryKey: ['admin', 'gallery', 'album', expandedAlbumId],
    queryFn: () => adminGalleryApi.getAlbum(expandedAlbumId!),
    enabled: !!expandedAlbumId,
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
    onSuccess: () => {
      setLocalOrder(null)
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
    const formData = new FormData(e.currentTarget)
    const data: UpdateAlbumRequest = {
      name: formData.get('name') as string,
      description: formData.get('description') as string || undefined,
    }
    updateAlbumMutation.mutate({ id: selectedAlbum.id, data })
  }

  const handleUploadPhoto = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedAlbum || selectedFiles.length === 0) return

    const formData = new FormData(e.currentTarget)
    const caption = formData.get('caption') as string

    setUploadProgress(0)

    // Upload all files sequentially
    for (let i = 0; i < selectedFiles.length; i++) {
      try {
        await uploadPhotoMutation.mutateAsync({
          albumId: selectedAlbum.id,
          file: selectedFiles[i],
          caption: selectedFiles.length === 1 ? caption : undefined,
        })
        setUploadProgress(Math.round(((i + 1) / selectedFiles.length) * 100))
      } catch (error) {
        console.error('Failed to upload photo:', error)
        break
      }
    }

    // Cleanup and close
    queryClient.invalidateQueries({ queryKey: ['admin', 'gallery'] })
    setUploadPhotoModalOpen(false)
    setPhotoPreviews([])
    setSelectedFiles([])
    setUploadProgress(0)
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

  const handleUpdatePhoto = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!selectedPhoto) return
    const formData = new FormData(e.currentTarget)
    const caption = formData.get('caption') as string
    updatePhotoMutation.mutate({
      photoId: selectedPhoto.id,
      data: {
        caption: caption || undefined,
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
        <h2 className="text-2xl font-bold text-dark-100">Zarządzanie Galerią</h2>
        <Button onClick={() => setCreateAlbumModalOpen(true)}>
          <Plus className="h-4 w-4 mr-2" />
          Nowy album
        </Button>
      </div>

      {/* Albums List */}
      {orderedAlbums.length === 0 && !isLoading ? (
        <div className="text-center py-12 text-dark-400">
          Brak albumów. Dodaj pierwszy używając przycisku powyżej.
        </div>
      ) : (
        <div className="space-y-4">
          {orderedAlbums.map((album, index) => (
            <div
              key={album.id}
              className="bg-dark-800 rounded-lg border border-dark-700 overflow-hidden"
            >
              {/* Album Header */}
              <div className="p-6">
                <div className="flex items-start gap-6">
                  {/* Thumbnail */}
                  <div className="flex-shrink-0">
                    {album.thumbnailUrl ? (
                      <div className="w-32 h-24 rounded-lg overflow-hidden relative bg-dark-700">
                        <img
                          src={album.thumbnailUrl}
                          alt=""
                          aria-hidden="true"
                          className="absolute inset-0 w-full h-full object-cover blur-xl scale-110"
                          style={album.thumbnailFocalPointX != null ? { objectPosition: `${album.thumbnailFocalPointX * 100}% ${(album.thumbnailFocalPointY ?? 0.5) * 100}%` } : undefined}
                        />
                        <img
                          src={album.thumbnailUrl}
                          alt={album.name}
                          className="relative w-full h-full object-contain"
                        />
                      </div>
                    ) : (
                      <div className="w-32 h-24 bg-dark-700 rounded-lg flex items-center justify-center">
                        <ImageIcon className="h-8 w-8 text-dark-500" />
                      </div>
                    )}
                  </div>

                  {/* Info */}
                  <div className="flex-1">
                    <div className="flex items-start justify-between">
                      <div>
                        <h3 className="text-xl font-bold text-dark-100">{album.name}</h3>
                        <p className="text-sm text-dark-400 mt-1">
                          {album.photoCount} {album.photoCount === 1 ? 'zdjęcie' : 'zdjęć'}
                        </p>
                        {album.description && (
                          <p className="text-dark-300 mt-2 text-sm">{album.description}</p>
                        )}
                      </div>
                      <div className="flex gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleMove(index, 'up')}
                          disabled={index === 0 || reorderMutation.isPending}
                          title="Przesuń wyżej"
                        >
                          <ChevronUp className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleMove(index, 'down')}
                          disabled={index === orderedAlbums.length - 1 || reorderMutation.isPending}
                          title="Przesuń niżej"
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
                          {expandedAlbumId === album.id ? 'Zwiń' : 'Rozwiń'}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setSelectedAlbum(album)
                            setEditAlbumModalOpen(true)
                          }}
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
                <div className="border-t border-dark-700 p-6 bg-dark-900/50">
                  <div className="flex items-center justify-between mb-4">
                    <h4 className="font-semibold text-dark-100">Zdjęcia w albumie</h4>
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
                          Usuń wszystkie
                        </Button>
                      )}
                      <Button
                        size="sm"
                        onClick={() => {
                          setSelectedAlbum(album)
                          setUploadPhotoModalOpen(true)
                        }}
                      >
                        <Upload className="h-4 w-4 mr-2" />
                        Dodaj zdjęcie
                      </Button>
                    </div>
                  </div>

                  {albumDetail.photos.length === 0 ? (
                    <div className="text-center py-8 text-dark-400 text-sm">
                      Ten album nie zawiera jeszcze zdjęć
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                      {albumDetail.photos.map((photo) => (
                        <div key={photo.id} className="group relative bg-dark-700 rounded-lg overflow-hidden aspect-square">
                          <img
                            src={photo.url}
                            alt=""
                            aria-hidden="true"
                            className="absolute inset-0 w-full h-full object-cover blur-xl scale-110"
                          />
                          <img
                            src={photo.url}
                            alt={photo.caption || ''}
                            className="relative w-full h-full object-contain"
                          />
                          {photo.caption && (
                            <div className="p-2 text-xs text-dark-300 line-clamp-2">
                              {photo.caption}
                            </div>
                          )}
                          {/* Thumbnail badge — always visible */}
                          {albumDetail.thumbnailPhotoId === photo.id && (
                            <div
                              className="absolute top-2 left-2 bg-amber-400 rounded-full p-1 shadow-md"
                              title="Miniaturka albumu"
                            >
                              <Star className="h-3 w-3 text-dark-900" fill="currentColor" />
                            </div>
                          )}
                          <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                            {/* Set as thumbnail */}
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                if (expandedAlbumId) {
                                  setThumbnailMutation.mutate({ albumId: expandedAlbumId, photoId: photo.id })
                                }
                              }}
                              disabled={setThumbnailMutation.isPending}
                              title={albumDetail.thumbnailPhotoId === photo.id ? 'Aktualnie ustawiona miniaturka' : 'Ustaw jako miniaturkę'}
                              className={`bg-dark-800/80 hover:bg-dark-700${albumDetail.thumbnailPhotoId === photo.id ? ' text-amber-400' : ''}`}
                            >
                              <Star
                                className="h-4 w-4"
                                fill={albumDetail.thumbnailPhotoId === photo.id ? 'currentColor' : 'none'}
                              />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setSelectedPhoto({ id: photo.id, caption: photo.caption, url: photo.url, focalPointX: photo.focalPointX, focalPointY: photo.focalPointY })
                                setFocalPoint({ x: photo.focalPointX ?? 0.5, y: photo.focalPointY ?? 0.5 })
                                setEditPhotoModalOpen(true)
                              }}
                              className="bg-dark-800/80 hover:bg-dark-700"
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setSelectedPhotoId(photo.id)
                                setDeletePhotoConfirmOpen(true)
                              }}
                              className="bg-dark-800/80 hover:bg-dark-700"
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
        title="Nowy Album"
      >
        <form onSubmit={handleCreateAlbum} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">
              Nazwa albumu *
            </label>
            <input
              type="text"
              name="name"
              required
              maxLength={255}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-dark-200 mb-1">
              Opis
            </label>
            <textarea
              name="description"
              rows={3}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          <div className="flex gap-2 justify-end">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setCreateAlbumModalOpen(false)}
            >
              Anuluj
            </Button>
            <Button type="submit" loading={createAlbumMutation.isPending}>
              Utwórz
            </Button>
          </div>

          {createAlbumMutation.error && (
            <div className="text-rose-400 text-sm">{String(createAlbumMutation.error)}</div>
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
          title="Edytuj Album"
        >
          <form onSubmit={handleUpdateAlbum} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Nazwa albumu *
              </label>
              <input
                type="text"
                name="name"
                defaultValue={selectedAlbum.name}
                required
                maxLength={255}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Opis
              </label>
              <textarea
                name="description"
                defaultValue={selectedAlbum.description || ''}
                rows={3}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div className="flex gap-2 justify-end">
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setEditAlbumModalOpen(false)
                  setSelectedAlbum(null)
                }}
              >
                Anuluj
              </Button>
              <Button type="submit" loading={updateAlbumMutation.isPending}>
                Zapisz
              </Button>
            </div>

            {updateAlbumMutation.error && (
              <div className="text-rose-400 text-sm">{String(updateAlbumMutation.error)}</div>
            )}
          </form>
        </Modal>
      )}

      {/* Upload Photo Modal */}
      {selectedAlbum && (
        <Modal
          isOpen={uploadPhotoModalOpen}
          onClose={() => {
            setUploadPhotoModalOpen(false)
            setSelectedAlbum(null)
            setPhotoPreviews([])
            setSelectedFiles([])
            setUploadProgress(0)
          }}
          title="Dodaj Zdjęcia"
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
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Podpis (opcjonalnie, tylko dla pojedynczego zdjęcia)
              </label>
              <input
                type="text"
                name="caption"
                disabled={selectedFiles.length > 1}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:opacity-50"
              />
            </div>

            {uploadProgress > 0 && uploadProgress < 100 && (
              <div className="space-y-2">
                <div className="flex justify-between text-sm text-dark-300">
                  <span>Przesyłanie...</span>
                  <span>{uploadProgress}%</span>
                </div>
                <div className="w-full bg-dark-700 rounded-full h-2 overflow-hidden">
                  <div
                    className="bg-primary-500 h-full transition-all duration-300"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
              </div>
            )}

            <div className="flex gap-2 justify-end">
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setUploadPhotoModalOpen(false)
                  setSelectedAlbum(null)
                  setPhotoPreviews([])
                  setSelectedFiles([])
                  setUploadProgress(0)
                }}
                disabled={uploadPhotoMutation.isPending}
              >
                Anuluj
              </Button>
              <Button
                type="submit"
                disabled={selectedFiles.length === 0 || uploadPhotoMutation.isPending}
                loading={uploadPhotoMutation.isPending}
              >
                Prześlij {selectedFiles.length > 1 ? `(${selectedFiles.length})` : ''}
              </Button>
            </div>

            {uploadPhotoMutation.error && (
              <div className="text-rose-400 text-sm">{String(uploadPhotoMutation.error)}</div>
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
          title="Edytuj Zdjęcie"
        >
          <form onSubmit={handleUpdatePhoto} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-dark-200 mb-1">
                Podpis
              </label>
              <textarea
                name="caption"
                defaultValue={selectedPhoto.caption || ''}
                rows={2}
                className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                placeholder="Dodaj opis zdjęcia..."
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-dark-200">
                  Kadrowanie
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
                imageUrl={selectedPhoto.url}
                value={focalPoint}
                onChange={setFocalPoint}
                aspectRatio="1/1"
              />
            </div>

            <div className="flex gap-2 justify-end">
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setEditPhotoModalOpen(false)
                  setSelectedPhoto(null)
                }}
              >
                Anuluj
              </Button>
              <Button
                type="submit"
                loading={updatePhotoMutation.isPending}
              >
                Zapisz
              </Button>
            </div>

            {updatePhotoMutation.error && (
              <div className="text-rose-400 text-sm">{String(updatePhotoMutation.error)}</div>
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
        title="Usuń Album"
        message={`Czy na pewno chcesz usunąć album "${selectedAlbum?.name}"? Wszystkie zdjęcia w tym albumie zostaną bezpowrotnie usunięte. Tej operacji nie można cofnąć.`}
        confirmText="Usuń album"
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
        title="Usuń Zdjęcie"
        message="Czy na pewno chcesz usunąć to zdjęcie? Tej operacji nie można cofnąć."
        confirmText="Usuń"
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
        title="Usuń wszystkie zdjęcia"
        message={`Czy na pewno chcesz usunąć wszystkie zdjęcia z albumu "${selectedAlbum?.name}"? Album zostanie zachowany. Tej operacji nie można cofnąć.`}
        confirmText="Usuń wszystkie zdjęcia"
      />
    </div>
  )
}

