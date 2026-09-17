import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useI18n } from '../i18n'
import { errorText } from '../components/Toast'
import { DotGrid } from '../components/DotGrid'
import { useIsPhone } from '../lib/mobile'
import { Field, Spinner } from '../components/ui'

/**
 * The way in, in the brand's own clothes: the painted dots across the whole page, with the form on a
 * clean card in the middle of them. The pattern is the same on a phone and on a wall-sized screen;
 * only how much of it you see changes.
 */
export function LoginPage() {
  const { t, lang, setLang } = useI18n()
  const { user, login } = useAuth()
  const phone = useIsPhone()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  if (user) return <Navigate to={from} replace />

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(errorText(err, t))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="relative min-h-dvh overflow-hidden bg-surface">
      <div className="pointer-events-none absolute inset-0">
        {/* Smaller dots on a phone, so the pattern still reads as a pattern on a narrow screen. */}
        <DotGrid className="size-full" scale={phone ? 0.58 : 1} />
      </div>

      <div className="relative flex min-h-dvh flex-col px-5 py-6 sm:px-8">
        <div className="flex justify-end">
          <button
            type="button"
            className="rounded-xl bg-surface/80 px-3 py-1.5 text-xs font-semibold text-ink backdrop-blur hover:bg-surface"
            onClick={() => setLang(lang === 'ka' ? 'en' : 'ka')}
          >
            {lang === 'ka' ? 'English' : 'ქართული'}
          </button>
        </div>

        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center">
          <div className="card border-line/70 bg-surface p-6 shadow-[0_24px_60px_rgba(28,21,35,0.22)] sm:p-8">
            <div className="mb-6 flex items-center gap-3">
              <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-brand-600 text-white">
                <svg viewBox="0 0 64 64" className="size-6" aria-hidden>
                  <path d="M22 50c-6 0-10-4-10-8 0-3 2-6 5-8l12-24h6l12 24c3 2 5 5 5 8 0 4-4 8-10 8-3 0-5-1-7-3-2 2-4 3-6 3-2 0-4-1-5-2-1 1-1 2-2 2z" fill="currentColor" />
                </svg>
              </span>
              <div className="min-w-0">
                <div className="text-lg font-bold leading-tight tracking-tight">ANDANERI</div>
                <div className="truncate text-xs text-muted">Taste Laboratory</div>
              </div>
            </div>

            <h1 className="text-2xl font-semibold tracking-tight">{t('auth.title')}</h1>
            <p className="mt-1 text-sm text-muted">{t('auth.subtitle')}</p>

            <form className="mt-6 space-y-4" onSubmit={submit}>
              <Field label={t('auth.username')}>
                <input className="input py-2.5 text-base" autoFocus autoComplete="username" autoCapitalize="none" value={username} onChange={(e) => setUsername(e.target.value)} />
              </Field>
              <Field label={t('auth.password')}>
                <input type="password" className="input py-2.5 text-base" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
              </Field>
              {error && <p className="rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
              <button type="submit" className="btn-primary w-full py-3 text-base" disabled={busy || !username || !password}>
                {busy && <Spinner className="size-4" />} {t('auth.signIn')}
              </button>
            </form>
          </div>

          <p className="mx-auto mt-5 rounded-full bg-surface/90 px-3.5 py-1.5 text-center text-xs text-ink/75 backdrop-blur-sm">
            {lang === 'ka' ? 'დახატე გემო 2026 წლის ტრენდული სიროფებით' : 'Paint the taste with the 2026 trend syrups'}
          </p>
        </div>
      </div>
    </div>
  )
}
