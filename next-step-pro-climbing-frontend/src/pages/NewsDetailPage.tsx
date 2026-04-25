import { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, Star, Share2, Copy, Check } from 'lucide-react'
import { newsApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { renderRichText } from '../utils/renderRichText'
import { useAuth } from '../context/AuthContext'
import clsx from 'clsx'

function ShareButtons({ title }: { title: string }) {
  const { t } = useTranslation('common')
  const [copied, setCopied] = useState(false)

  const url = window.location.href
  const encodedUrl = encodeURIComponent(url)
  const encodedTitle = encodeURIComponent(title)

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // clipboard not available
    }
  }, [url])

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-dark-500">{t('news.share.shareOn')}</span>
      <a
        href={`https://www.facebook.com/sharer/sharer.php?u=${encodedUrl}`}
        target="_blank"
        rel="noopener noreferrer"
        title="Facebook"
        className="p-1.5 rounded text-dark-400 hover:text-[#1877F2] hover:bg-dark-700 transition-colors"
        aria-label="Facebook"
      >
        <Share2 className="w-4 h-4" />
      </a>
      <a
        href={`https://wa.me/?text=${encodedTitle}%0A${encodedUrl}`}
        target="_blank"
        rel="noopener noreferrer"
        title="WhatsApp"
        className="p-1.5 rounded text-dark-400 hover:text-[#25D366] hover:bg-dark-700 transition-colors"
        aria-label="WhatsApp"
      >
        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
          <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
        </svg>
      </a>
      <button
        onClick={handleCopy}
        title={copied ? t('news.share.copied') : t('news.share.copy')}
        className="p-1.5 rounded text-dark-400 hover:text-dark-100 hover:bg-dark-700 transition-colors"
        aria-label={copied ? t('news.share.copied') : t('news.share.copy')}
      >
        {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
      </button>
    </div>
  )
}

export function NewsDetailPage() {
  const { t } = useTranslation('common')
  const { newsId } = useParams<{ newsId: string }>()
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  const { data: article, isLoading, error } = useQuery({
    queryKey: ['news', newsId],
    queryFn: () => newsApi.getById(newsId!),
    enabled: !!newsId,
  })

  const starMutation = useMutation({
    mutationFn: (isStarred: boolean) =>
      isStarred ? newsApi.unstar(newsId!) : newsApi.star(newsId!),
    onMutate: async (isStarred) => {
      await queryClient.cancelQueries({ queryKey: ['news', newsId] })
      const previous = queryClient.getQueryData(['news', newsId])
      queryClient.setQueryData(['news', newsId], (old: typeof article) => {
        if (!old) return old
        return { ...old, starred: !isStarred }
      })
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(['news', newsId], context.previous)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] })
    },
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

  if (!article) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-dark-400">{t('news.articleNotFound')}</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      {/* Breadcrumb */}
      <Link
        to="/aktualnosci"
        className="inline-flex items-center gap-2 text-dark-300 hover:text-dark-100 mb-6 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        {t('news.backToNews')}
      </Link>

      {/* Header */}
      <div className="mb-8">
        <div className="flex items-start justify-between gap-4">
          <h1 className="text-3xl font-bold text-dark-100 mb-3">{article.title}</h1>
          {isAuthenticated && (
            <button
              onClick={() => starMutation.mutate(article.starred === true)}
              aria-label={article.starred ? t('news.starRemove') : t('news.starAdd')}
              disabled={starMutation.isPending}
              className={clsx(
                'flex-shrink-0 mt-1 p-2 rounded-full transition-colors',
                article.starred
                  ? 'text-yellow-400 hover:bg-dark-700'
                  : 'text-dark-400 hover:text-yellow-400 hover:bg-dark-700'
              )}
            >
              <Star className={clsx('h-5 w-5', article.starred && 'fill-yellow-400')} />
            </button>
          )}
        </div>
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <p className="text-sm text-dark-400">
            {t('news.published')}: {new Date(article.publishedAt).toLocaleDateString('pl-PL', {
              day: 'numeric',
              month: 'long',
              year: 'numeric',
            })}
          </p>
          <ShareButtons title={article.title} />
        </div>
      </div>

      <hr className="border-dark-700 mb-8" />

      {/* Bloki treści */}
      <div className="space-y-6">
        {article.blocks.map((block) => {
          if (block.blockType === 'TEXT') {
            return (
              <div
                key={block.id}
                className="text-dark-200 leading-relaxed"
                dangerouslySetInnerHTML={{ __html: renderRichText(block.content ?? '') }}
              />
            )
          }

          if (block.blockType === 'IMAGE') {
            return (
              <figure key={block.id} className="my-6">
                <img
                  src={block.imageUrl ?? ''}
                  alt={block.caption ?? ''}
                  className="w-full rounded-lg"
                />
                {block.caption && (
                  <figcaption className="text-sm text-dark-400 text-center mt-2">
                    {block.caption}
                  </figcaption>
                )}
              </figure>
            )
          }

          if (block.blockType === 'VIDEO_EMBED' && block.content) {
            return (
              <div key={block.id} className="my-6 w-full" style={{ aspectRatio: '16/9' }}>
                <iframe
                  src={block.content}
                  className="w-full h-full rounded-lg"
                  allowFullScreen
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  loading="lazy"
                />
              </div>
            )
          }

          return null
        })}
      </div>
    </div>
  )
}
