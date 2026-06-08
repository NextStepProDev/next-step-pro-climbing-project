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
 * Wybiera najlepsze dostępne tłumaczenie dla preferowanego języka.
 * Jeśli preferowany język istnieje — zwraca jego wersję. Jeśli nie —
 * fallback w kolejności: preferowany → EN → PL → ES → pierwsze dostępne.
 * Dzięki temu np. przy wyborze ES, gdy są tylko PL i EN, otwiera się EN.
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
