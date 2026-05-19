import { Helmet } from 'react-helmet-async'

const SITE_NAME = 'Next Step Pro Climbing'
const BASE_URL = 'https://nextsteppro.pl'

interface PageHeadProps {
  title?: string
  description?: string
  path?: string
  ogImage?: string
  availableLanguages?: string[]
  currentLanguage?: string
}

export function PageHead({ title, description, path, ogImage, availableLanguages, currentLanguage }: PageHeadProps) {
  const fullTitle = title ? `${title} | ${SITE_NAME}` : SITE_NAME
  const ogImageUrl = ogImage ?? `${BASE_URL}/og-image.jpg`

  return (
    <Helmet>
      <title>{fullTitle}</title>
      {description && <meta name="description" content={description} />}
      {path && <link rel="canonical" href={`${BASE_URL}${path}`} />}

      <meta property="og:title" content={fullTitle} />
      {description && <meta property="og:description" content={description} />}
      <meta property="og:image" content={ogImageUrl} />
      <meta property="og:type" content="website" />
      {path && <meta property="og:url" content={`${BASE_URL}${path}`} />}

      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:title" content={fullTitle} />
      {description && <meta name="twitter:description" content={description} />}
      <meta name="twitter:image" content={ogImageUrl} />

      {availableLanguages && currentLanguage && path && availableLanguages.map(lang => (
        <link
          key={lang}
          rel="alternate"
          hrefLang={lang}
          href={`${BASE_URL}${path}${path.includes('?') ? '&' : '?'}language=${lang}`}
        />
      ))}
      {availableLanguages && currentLanguage && path && (
        <link rel="alternate" hrefLang="x-default" href={`${BASE_URL}${path}?language=pl`} />
      )}
    </Helmet>
  )
}
