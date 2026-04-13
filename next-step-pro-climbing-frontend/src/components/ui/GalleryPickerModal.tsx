import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ChevronLeft } from 'lucide-react'
import { useState } from 'react'
import { galleryApi } from '../../api/client'
import type { Photo } from '../../types'
import { Modal } from './Modal'
import { LoadingSpinner } from './LoadingSpinner'

interface GalleryPickerModalProps {
  isOpen: boolean
  onClose: () => void
  onSelect: (photoUrl: string) => void
}

export function GalleryPickerModal({ isOpen, onClose, onSelect }: GalleryPickerModalProps) {
  const { t } = useTranslation('admin')
  const [selectedAlbumId, setSelectedAlbumId] = useState<string | null>(null)

  const { data: albums, isLoading: albumsLoading } = useQuery({
    queryKey: ['gallery', 'albums'],
    queryFn: galleryApi.getAlbums,
    enabled: isOpen,
  })

  const { data: albumDetail, isLoading: photosLoading } = useQuery({
    queryKey: ['gallery', 'album', selectedAlbumId],
    queryFn: () => galleryApi.getAlbum(selectedAlbumId!),
    enabled: isOpen && selectedAlbumId !== null,
  })

  function handleSelectPhoto(photo: Photo) {
    onSelect(photo.url)
    setSelectedAlbumId(null)
    onClose()
  }

  function handleClose() {
    setSelectedAlbumId(null)
    onClose()
  }

  const isLoading = albumsLoading || photosLoading

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={t('galleryPicker.title')} size="xl">
      {isLoading ? (
        <div className="flex justify-center py-8">
          <LoadingSpinner />
        </div>
      ) : selectedAlbumId === null ? (
        !albums || albums.length === 0 ? (
          <p className="text-center text-dark-400 py-8">{t('galleryPicker.noAlbums')}</p>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {albums.map((album) => (
              <button
                key={album.id}
                onClick={() => setSelectedAlbumId(album.id)}
                className="relative aspect-video rounded-lg overflow-hidden border-2 border-dark-700 hover:border-dark-500 transition-colors group"
              >
                {album.thumbnailUrl ? (
                  <img
                    src={album.thumbnailUrl}
                    alt={album.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full bg-dark-700 flex items-center justify-center">
                    <span className="text-dark-400 text-xs">{t('galleryPicker.noThumbnail')}</span>
                  </div>
                )}
                <div className="absolute inset-0 bg-black/40 group-hover:bg-black/50 transition-colors flex items-end">
                  <span className="w-full px-2 py-1.5 text-sm font-medium text-white truncate">
                    {album.name}
                  </span>
                </div>
              </button>
            ))}
          </div>
        )
      ) : (
        <>
          <button
            onClick={() => setSelectedAlbumId(null)}
            className="flex items-center gap-1 text-sm text-dark-300 hover:text-dark-100 transition-colors mb-4"
          >
            <ChevronLeft className="h-4 w-4" />
            {t('galleryPicker.backToAlbums')}
          </button>
          {!albumDetail || albumDetail.photos.length === 0 ? (
            <p className="text-center text-dark-400 py-8">{t('galleryPicker.noPhotos')}</p>
          ) : (
            <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
              {albumDetail.photos.map((photo) => (
                <button
                  key={photo.id}
                  onClick={() => handleSelectPhoto(photo)}
                  className="relative aspect-square rounded-lg overflow-hidden border-2 border-dark-700 hover:border-dark-500 transition-colors"
                >
                  <img
                    src={photo.url}
                    alt={photo.caption ?? ''}
                    className="w-full h-full object-cover"
                  />
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </Modal>
  )
}
