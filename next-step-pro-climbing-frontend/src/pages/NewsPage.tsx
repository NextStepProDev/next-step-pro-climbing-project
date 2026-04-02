import { useInfiniteQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Newspaper } from 'lucide-react'
import { newsApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'

export function NewsPage() {
  const { t } = useTranslation('common')

  const {
    data,
    isLoading,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['news'],
    queryFn: ({ pageParam }) => newsApi.getAll(pageParam as number),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    staleTime: 5 * 60 * 1000,
  })

  const articles = data?.pages.flatMap(p => p.content) ?? []

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

  if (articles.length === 0) {
    return (
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('news.title')}</h1>
        <div className="text-center text-dark-400 py-12">
          {t('news.noArticles')}
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('news.title')}</h1>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {articles.map((article) => (
          <Link
            key={article.id}
            to={`/aktualnosci/${article.id}`}
            className="group bg-dark-800 rounded-lg overflow-hidden border border-dark-700 hover:border-primary-500/50 transition-colors"
          >
            <div className="aspect-video bg-dark-700 relative overflow-hidden">
              {article.thumbnailUrl ? (
                <img
                  src={article.thumbnailUrl}
                  alt={article.title}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center">
                  <Newspaper className="h-16 w-16 text-dark-500" />
                </div>
              )}
              <div className="absolute bottom-2 left-2 bg-dark-900/80 px-2 py-1 rounded text-xs text-dark-300">
                {new Date(article.publishedAt).toLocaleDateString('pl-PL', {
                  day: 'numeric',
                  month: 'short',
                  year: 'numeric',
                })}
              </div>
            </div>

            <div className="p-4">
              <h2 className="text-lg font-semibold text-dark-100 group-hover:text-primary-400 transition-colors">
                {article.title}
              </h2>
              {article.excerpt && (
                <p className="mt-2 text-sm text-dark-300 line-clamp-3">
                  {article.excerpt}
                </p>
              )}
            </div>
          </Link>
        ))}
      </div>

      {hasNextPage && (
        <div className="mt-8 flex justify-center">
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="px-6 py-2 bg-dark-700 hover:bg-dark-600 text-dark-100 rounded-lg transition-colors disabled:opacity-50"
          >
            {isFetchingNextPage ? <LoadingSpinner /> : t('news.loadMore')}
          </button>
        </div>
      )}
    </div>
  )
}
