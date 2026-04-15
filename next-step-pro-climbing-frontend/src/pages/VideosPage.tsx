import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ExternalLink, Play } from 'lucide-react'
import { videoApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { renderRichText } from '../utils/renderRichText'

function extractYouTubeId(url: string): string | null {
  try {
    const u = new URL(url)
    if (u.hostname === 'youtu.be') {
      return u.pathname.slice(1)
    }
    if (u.pathname.startsWith('/shorts/')) {
      return u.pathname.slice('/shorts/'.length)
    }
    return u.searchParams.get('v')
  } catch {
    return null
  }
}

function getYouTubeThumbnail(url: string): string | null {
  const id = extractYouTubeId(url)
  if (!id) return null
  return `https://img.youtube.com/vi/${id}/hqdefault.jpg`
}

export function VideosPage() {
  const { t } = useTranslation('common')
  const { data: videos, isLoading, error } = useQuery({
    queryKey: ['videos'],
    queryFn: videoApi.getAll,
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

  if (!videos || videos.length === 0) {
    return (
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('videos.title')}</h1>
        <div className="text-center text-dark-400">{t('videos.noVideos')}</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('videos.title')}</h1>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {videos.map((video) => {
          const thumbnail = getYouTubeThumbnail(video.youtubeUrl)
          return (
            <div
              key={video.id}
              className="bg-dark-800 rounded-lg overflow-hidden border border-dark-700 flex flex-col"
            >
              {/* Thumbnail */}
              <a
                href={video.youtubeUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="group block aspect-video bg-dark-700 relative overflow-hidden"
                aria-label={video.title}
              >
                {thumbnail ? (
                  <img
                    src={thumbnail}
                    alt={video.title}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center">
                    <Play className="w-16 h-16 text-dark-500" />
                  </div>
                )}
                {/* Play overlay */}
                <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-black/30">
                  <div className="w-14 h-14 rounded-full bg-red-600 flex items-center justify-center shadow-lg">
                    <Play className="w-7 h-7 text-white fill-white ml-1" />
                  </div>
                </div>
              </a>

              {/* Content */}
              <div className="p-4 flex flex-col flex-1">
                <h2 className="text-lg font-semibold text-dark-100 mb-2">
                  {video.title}
                </h2>

                {video.excerpt && (
                  <div
                    className="text-sm text-dark-300 mb-3 line-clamp-3 prose-sm"
                    dangerouslySetInnerHTML={{ __html: renderRichText(video.excerpt) }}
                  />
                )}

                {video.content && (
                  <div
                    className="text-sm text-dark-400 mb-3"
                    dangerouslySetInnerHTML={{ __html: renderRichText(video.content) }}
                  />
                )}

                <div className="mt-auto pt-3">
                  <a
                    href={video.youtubeUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors"
                  >
                    <ExternalLink className="w-4 h-4" />
                    {t('videos.watchOnYoutube')}
                  </a>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
