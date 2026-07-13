export const COURSE_CONTENT_LANGUAGES = [
  { code: 'pl', label: 'PL' },
  { code: 'en', label: 'EN' },
  { code: 'es', label: 'ES' },
] as const

export type CourseContentLanguageCode = typeof COURSE_CONTENT_LANGUAGES[number]['code']

export function getDefaultCourseContentLanguage(uiLanguage: string): CourseContentLanguageCode {
  if (uiLanguage === 'pl') return 'pl'
  if (uiLanguage === 'es') return 'es'
  return 'en'
}

/**
 * Picks the best available translation for the preferred language.
 * If the preferred language exists — returns its version. If not —
 * falls back in order: preferred → EN → PL → ES → first available.
 * E.g. with ES selected but only PL and EN available, EN opens.
 */
export function pickBestTranslation<T extends { language: string }>(
  translations: T[] | undefined,
  preferred: string,
): T | undefined {
  if (!translations || translations.length === 0) return undefined
  for (const code of [preferred, 'en', 'pl', 'es']) {
    const match = translations.find((tr) => tr.language === code)
    if (match) return match
  }
  return translations[0]
}
