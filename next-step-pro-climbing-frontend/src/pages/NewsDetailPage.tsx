import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { newsApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { renderRichText } from '../utils/renderRichText'

export function NewsDetailPage() {
  const { t } = useTranslation('common')
  const { newsId } = useParams<{ newsId: string }>()

  const { data: article, isLoading, error } = useQuery({
    queryKey: ['news', newsId],
    queryFn: () => newsApi.getById(newsId!),
    enabled: !!newsId,
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
        <h1 className="text-3xl font-bold text-dark-100 mb-3">{article.title}</h1>
        <p className="text-sm text-dark-400">
          {t('news.published')}: {new Date(article.publishedAt).toLocaleDateString('pl-PL', {
            day: 'numeric',
            month: 'long',
            year: 'numeric',
          })}
        </p>
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

          return null
        })}
      </div>
    </div>
  )
}
