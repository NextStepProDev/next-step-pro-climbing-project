import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { galleryApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { Lightbox } from '../components/gallery/Lightbox'

export function AlbumPage() {
  const { t } = useTranslation('common')
  const { albumId } = useParams<{ albumId: string }>()
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)

  const { data: album, isLoading, error } = useQuery({
    queryKey: ['gallery', 'album', albumId],
    queryFn: () => galleryApi.getAlbum(albumId!),
    enabled: !!albumId,
  })

  if (isLoading) {
    return (
      <div className="min-h-[50vh] flex items-center justify-center">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return (
      <div className="container mx-auto px-4 py-8">
        <QueryError error={error} />
      </div>
    )
  }

  if (!album) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-dark-400">{t('gallery.albumNotFound')}</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      {/* Breadcrumb */}
      <Link
        to="/galeria"
        className="inline-flex items-center gap-2 text-dark-300 hover:text-dark-100 mb-6 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        {t('gallery.backToGallery')}
      </Link>

      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-dark-100 mb-2">{album.name}</h1>
        {album.description && (
          <p className="text-dark-300">{album.description}</p>
        )}
        <p className="text-sm text-dark-400 mt-2">
          {album.photos.length} {t(`gallery.photo_${album.photos.length === 1 ? 'one' : 'many'}`)}
        </p>
      </div>

      {/* Photos Grid */}
      {album.photos.length === 0 ? (
        <div className="text-center text-dark-400 py-12">
          {t('gallery.noPhotos')}
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {album.photos.map((photo, index) => (
            <button
              key={photo.id}
              onClick={() => setLightboxIndex(index)}
              className="aspect-square bg-dark-700 rounded-lg overflow-hidden hover:ring-2 hover:ring-primary-500/50 transition-all group"
            >
              <img
                src={photo.url}
                alt={photo.caption || ''}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              />
            </button>
          ))}
        </div>
      )}

      {/* Lightbox */}
      {lightboxIndex !== null && (
        <Lightbox
          photos={album.photos}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </div>
  )
}
