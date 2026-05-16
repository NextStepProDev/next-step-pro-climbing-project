import { Helmet } from 'react-helmet-async'

const SITE_NAME = 'Next Step Pro Climbing'
const BASE_URL = 'https://nextsteppro.pl'

interface PageHeadProps {
  title?: string
  description?: string
  path?: string
  availableLanguages?: string[]
  currentLanguage?: string
}

export function PageHead({ title, description, path, availableLanguages, currentLanguage }: PageHeadProps) {
  const fullTitle = title ? `${title} | ${SITE_NAME}` : SITE_NAME

  return (
    <Helmet>
      <title>{fullTitle}</title>
      {description && <meta name="description" content={description} />}
      {path && <link rel="canonical" href={`${BASE_URL}${path}`} />}
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
