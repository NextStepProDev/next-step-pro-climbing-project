import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import commonPl from './locales/pl/common.json'
import authPl from './locales/pl/auth.json'
import calendarPl from './locales/pl/calendar.json'
import reservationsPl from './locales/pl/reservations.json'
import adminPl from './locales/pl/admin.json'
import settingsPl from './locales/pl/settings.json'
import homePl from './locales/pl/home.json'
import errorsPl from './locales/pl/errors.json'
import faqPl from './locales/pl/faq.json'
import trainingPl from './locales/pl/training.json'
import ascentsPl from './locales/pl/ascents.json'

import commonEn from './locales/en/common.json'
import authEn from './locales/en/auth.json'
import calendarEn from './locales/en/calendar.json'
import reservationsEn from './locales/en/reservations.json'
import adminEn from './locales/en/admin.json'
import settingsEn from './locales/en/settings.json'
import homeEn from './locales/en/home.json'
import errorsEn from './locales/en/errors.json'
import faqEn from './locales/en/faq.json'
import trainingEn from './locales/en/training.json'
import ascentsEn from './locales/en/ascents.json'

import commonEs from './locales/es/common.json'
import authEs from './locales/es/auth.json'
import calendarEs from './locales/es/calendar.json'
import reservationsEs from './locales/es/reservations.json'
import adminEs from './locales/es/admin.json'
import settingsEs from './locales/es/settings.json'
import homeEs from './locales/es/home.json'
import errorsEs from './locales/es/errors.json'
import faqEs from './locales/es/faq.json'
import trainingEs from './locales/es/training.json'
import ascentsEs from './locales/es/ascents.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      pl: {
        common: commonPl,
        auth: authPl,
        calendar: calendarPl,
        reservations: reservationsPl,
        admin: adminPl,
        settings: settingsPl,
        home: homePl,
        errors: errorsPl,
        faq: faqPl,
        training: trainingPl,
        ascents: ascentsPl,
      },
      en: {
        common: commonEn,
        auth: authEn,
        calendar: calendarEn,
        reservations: reservationsEn,
        admin: adminEn,
        settings: settingsEn,
        home: homeEn,
        errors: errorsEn,
        faq: faqEn,
        training: trainingEn,
        ascents: ascentsEn,
      },
      es: {
        common: commonEs,
        auth: authEs,
        calendar: calendarEs,
        reservations: reservationsEs,
        admin: adminEs,
        settings: settingsEs,
        home: homeEs,
        errors: errorsEs,
        faq: faqEs,
        training: trainingEs,
        ascents: ascentsEs,
      },
    },
    fallbackLng: 'en',
    // ⚠️ The three codes the rest of the app compares against. Browsers report a region ("pl-PL"),
    // and passed through, that read as "not Polish" everywhere a code is compared: a Polish visitor
    // got the English news, courses and team pages, the wrong date-fns locale and no highlighted
    // language. Cut to two letters at detection (below), checked against this list here.
    supportedLngs: ['pl', 'en', 'es'],
    defaultNS: 'common',
    ns: ['common', 'auth', 'calendar', 'reservations', 'admin', 'settings', 'home', 'errors', 'faq', 'training', 'ascents'],
    interpolation: {
      escapeValue: false,
    },
    detection: {
      // 'querystring' must lead: PageHead emits hreflang alternates as
      // ?language=xx (NewsPage/CoursesPage/TeamPage), and without this, visiting
      // that URL rendered whatever localStorage/navigator picked — i.e. never the
      // language the link promised. Google flagged those alternates as soft 404s.
      order: ['querystring', 'localStorage', 'navigator'],
      lookupQuerystring: 'language',
      caches: ['localStorage'],
      // Also rewrites a regional code an earlier visit cached in localStorage.
      convertDetectedLanguage: (lng: string) => lng.split('-')[0].toLowerCase(),
      lookupLocalStorage: 'i18nextLng',
    },
  })

export default i18n
