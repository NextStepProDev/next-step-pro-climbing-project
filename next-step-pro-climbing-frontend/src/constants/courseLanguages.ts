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
