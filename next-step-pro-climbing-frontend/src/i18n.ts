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

import commonEn from './locales/en/common.json'
import authEn from './locales/en/auth.json'
import calendarEn from './locales/en/calendar.json'
import reservationsEn from './locales/en/reservations.json'
import adminEn from './locales/en/admin.json'
import settingsEn from './locales/en/settings.json'
import homeEn from './locales/en/home.json'
import errorsEn from './locales/en/errors.json'

import commonEs from './locales/es/common.json'
import authEs from './locales/es/auth.json'
import calendarEs from './locales/es/calendar.json'
import reservationsEs from './locales/es/reservations.json'
import adminEs from './locales/es/admin.json'
import settingsEs from './locales/es/settings.json'
import homeEs from './locales/es/home.json'
import errorsEs from './locales/es/errors.json'

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
      },
    },
    fallbackLng: 'pl',
    defaultNS: 'common',
    ns: ['common', 'auth', 'calendar', 'reservations', 'admin', 'settings', 'home', 'errors'],
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'i18nextLng',
    },
  })

export default i18n
