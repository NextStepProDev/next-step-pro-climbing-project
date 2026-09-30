import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getDefaultCourseContentLanguage } from '../constants/courseLanguages'

/**
 * Which language version of CMS content a list shows. Starts at the UI language and follows the
 * global switcher, but the page's own PL/EN/ES pills can pick another one in between.
 *
 * Held in state, so it resets on remount. A page that must keep the choice across navigation
 * keeps it in the URL instead (TeamPage, whose member modal is a route of its own).
 */
export function useContentLanguage() {
  const { i18n } = useTranslation()
  const [contentLanguage, setContentLanguage] = useState<string>(() =>
    getDefaultCourseContentLanguage(i18n.language)
  )

  useEffect(() => {
    const handler = (lng: string) => setContentLanguage(getDefaultCourseContentLanguage(lng))
    i18n.on('languageChanged', handler)
    return () => { i18n.off('languageChanged', handler) }
  }, [i18n])

  return [contentLanguage, setContentLanguage] as const
}
