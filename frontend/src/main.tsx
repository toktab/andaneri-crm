import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@fontsource-variable/inter'
import '@fontsource-variable/noto-sans-georgian'
import './index.css'
import { App } from './App'
import { I18nProvider } from './i18n'
import { AuthProvider } from './lib/auth'
import { ModeProvider } from './lib/mode'
import { ToastProvider } from './components/Toast'
import { ApiError } from './lib/api'
import { ThemeProvider } from './lib/theme'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      // A 401 or 403 will not fix itself by asking again.
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nProvider>
            <ToastProvider>
              <AuthProvider>
                <ModeProvider>
                  <App />
                </ModeProvider>
              </AuthProvider>
            </ToastProvider>
          </I18nProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
