import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'

interface FaqItem {
  question: string
  answer: string
}

interface FaqSection {
  title: string
  items: FaqItem[]
}

export function FAQPage() {
  const { t } = useTranslation('faq')
  const sections = t('sections', { returnObjects: true }) as FaqSection[]
  const [openItems, setOpenItems] = useState<Set<string>>(new Set())

  function toggleItem(key: string) {
    setOpenItems((prev) => {
      const next = new Set(prev)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  return (
    <div className="min-h-screen bg-dark-950">
      {/* Hero */}
      <div className="relative overflow-hidden bg-gradient-to-b from-dark-900 to-dark-950 border-b border-dark-800">
        <div className="absolute inset-0 opacity-5">
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-64 h-64 bg-primary-700 rounded-full blur-3xl" />
        </div>
        <div className="relative max-w-4xl mx-auto px-4 py-16 sm:py-24 text-center">
          <h1 className="text-3xl sm:text-4xl font-bold text-dark-100 mb-3">
            {t('hero.title')}
          </h1>
          <p className="text-dark-400 text-lg max-w-xl mx-auto">
            {t('hero.subtitle')}
          </p>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-3xl mx-auto px-4 py-12 sm:py-16 space-y-10">
        {sections.map((section) => (
          <section key={section.title}>
            <h2 className="text-lg font-semibold text-primary-400 mb-4 pb-2 border-b border-dark-800">
              {section.title}
            </h2>
            <div className="space-y-2">
              {section.items.map((item) => {
                const key = `${section.title}::${item.question}`
                const isOpen = openItems.has(key)
                return (
                  <div
                    key={key}
                    className="bg-dark-900 border border-dark-800 rounded-xl overflow-hidden"
                  >
                    <button
                      type="button"
                      onClick={() => toggleItem(key)}
                      className="w-full flex items-center justify-between gap-4 px-5 py-4 text-left hover:bg-dark-800/50 transition-colors"
                    >
                      <span className="text-dark-100 font-medium text-sm sm:text-base">
                        {item.question}
                      </span>
                      <ChevronDown
                        className={clsx(
                          'w-4 h-4 shrink-0 text-dark-500 transition-transform duration-200',
                          isOpen && 'rotate-180',
                        )}
                      />
                    </button>
                    {isOpen && (
                      <div className="px-5 pb-5 pt-1">
                        <p className="text-dark-400 text-sm sm:text-base leading-relaxed">
                          {item.answer}
                        </p>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </section>
        ))}

        {/* CTA */}
        <div className="bg-dark-900 border border-dark-800 rounded-2xl p-6 sm:p-8 text-center mt-12">
          <h3 className="text-dark-100 font-semibold text-lg mb-2">
            {t('cta.title')}
          </h3>
          <p className="text-dark-400 text-sm mb-4">
            {t('cta.subtitle')}
          </p>
          <a
            href="/kontakt"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-primary-600 hover:bg-primary-500 text-white text-sm font-medium rounded-lg transition-colors"
          >
            {t('cta.button')}
          </a>
        </div>
      </div>
    </div>
  )
}
