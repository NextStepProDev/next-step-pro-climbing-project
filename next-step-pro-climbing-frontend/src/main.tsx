import './i18n'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider, MutationCache, keepPreviousData } from '@tanstack/react-query'
import { HelmetProvider } from 'react-helmet-async'
import { ApiError } from './utils/errors'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import { ToastProvider } from './context/ToastContext'
import { TrainingClipboardProvider } from './context/TrainingClipboardContext'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  // Last-resort reporting for failed mutations.
  //
  // Roughly half the mutations in the app defined only `onSuccess`, so a rejected request —
  // a 409 from a business rule, a 403, a dropped connection — did literally nothing on screen:
  // the admin pressed Delete or Save and the UI sat there looking fine. Nothing warns you about
  // this; the handler is simply absent, which is why it survived review.
  //
  // Fires ONLY when the mutation has no onError of its own, so components that already report
  // failures keep sole ownership of their messaging and nothing is announced twice. Dispatched
  // as an event rather than calling the toast directly because the client is built outside the
  // React tree — same approach as `auth:session-expired` in api/client.ts.
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      if (mutation.options.onError) return
      window.dispatchEvent(
        new CustomEvent<string>('app:mutation-error', {
          detail: error instanceof Error ? error.message : String(error),
        }),
      )
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      // One retry, except on 429: the limiter's window is a fixed minute, so an immediate
      // retry cannot succeed and only spends another request against the bucket that is
      // already full — the failure mode feeds itself.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.isRateLimited) && failureCount < 1,
      // Keep the previous result on screen while a new queryKey is fetching
      // (pagination, month/filter/language switches, type-ahead search) instead
      // of dropping to an empty/loading branch and collapsing the layout.
      // Signal loading via `isFetching` (e.g. dimming), not by emptying the list.
      // Detail-by-id views opt out with `placeholderData: undefined` so they
      // never flash a stale entity — see those queries for the rationale.
      placeholderData: keepPreviousData,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <HelmetProvider>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <ToastProvider>
                {/* Above the router on purpose: an armed copy has to survive walking from one
                    athlete's calendar to another's, which is what makes cross-athlete paste work */}
                <TrainingClipboardProvider>
                  <App />
                </TrainingClipboardProvider>
              </ToastProvider>
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </HelmetProvider>
    </ThemeProvider>
  </StrictMode>,
)
