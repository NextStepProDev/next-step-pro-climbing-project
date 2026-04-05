import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Image as ImageIcon } from 'lucide-react'
import { galleryApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'

export function GalleryPage() {
  const { t } = useTranslation('common')
  const { data: albums, isLoading, error } = useQuery({
    queryKey: ['gallery', 'albums'],
    queryFn: galleryApi.getAlbums,
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

  if (!albums || albums.length === 0) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-dark-400">
          {t('gallery.noAlbums')}
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('gallery.title')}</h1>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {albums.map((album) => (
          <Link
            key={album.id}
            to={`/galeria/${album.id}`}
            className="group bg-dark-800 rounded-lg overflow-hidden border border-dark-700 hover:border-primary-500/50 transition-colors"
          >
            {/* Thumbnail */}
            <div className="aspect-video bg-dark-700 relative overflow-hidden">
              {album.thumbnailUrl ? (
                <>
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
                    className="relative w-full h-full object-contain group-hover:scale-105 transition-transform duration-300"
                  />
                </>
              ) : (
                <div className="w-full h-full flex items-center justify-center">
                  <ImageIcon className="h-16 w-16 text-dark-500" />
                </div>
              )}
              <div className="absolute top-2 right-2 bg-dark-900/80 px-2 py-1 rounded text-sm text-dark-200">
                {album.photoCount} {t(`gallery.photo_${album.photoCount === 1 ? 'one' : 'many'}`)}
              </div>
            </div>

            {/* Info */}
            <div className="p-4">
              <h2 className="text-lg font-semibold text-dark-100 group-hover:text-primary-400 transition-colors">
                {album.name}
              </h2>
              {album.description && (
                <p className="mt-2 text-sm text-dark-300 line-clamp-2">
                  {album.description}
                </p>
              )}
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
