import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useI18n } from '../i18n'
import { errorText } from '../components/Toast'
import { Field, Spinner } from '../components/ui'

export function LoginPage() {
  const { t, lang, setLang } = useI18n()
  const { user, login } = useAuth()
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
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="relative hidden overflow-hidden bg-brand-700 lg:block">
        {/* The four colour bands of the 2026 trends sheet. */}
        <div className="absolute inset-0 grid grid-cols-4">
          <div className="bg-[#c8139a]" />
          <div className="bg-violet-brand" />
          <div className="bg-lime-brand" />
          <div className="bg-raspberry" />
        </div>
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-black/10 to-transparent" />
        <div className="absolute bottom-10 left-10 right-10 text-white">
          <div className="text-4xl font-bold tracking-tight">ANDANERI</div>
          <div className="mt-1 text-lg opacity-90">Taste Laboratory</div>
          <p className="mt-6 max-w-sm text-sm opacity-80">
            {lang === 'ka' ? 'დახატე გემო 2026 წლის ტრენდული სიროფებით' : 'Paint the taste with the 2026 trend syrups'}
          </p>
        </div>
      </div>

      <div className="flex flex-col px-6 py-8">
        <div className="flex justify-end">
          <button type="button" className="btn-ghost text-xs font-semibold" onClick={() => setLang(lang === 'ka' ? 'en' : 'ka')}>
            {lang === 'ka' ? 'English' : 'ქართული'}
          </button>
        </div>
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center">
          <div className="mb-8">
            <div className="mb-4 grid size-12 place-items-center rounded-2xl bg-brand-600 text-white lg:hidden">
              <span className="text-xl font-bold">A</span>
            </div>
            <h1 className="text-2xl font-semibold tracking-tight">{t('auth.title')}</h1>
            <p className="mt-1 text-sm text-muted">Andaneri · {t('auth.subtitle')}</p>
          </div>
          <form className="space-y-4" onSubmit={submit}>
            <Field label={t('auth.username')}>
              <input className="input py-2.5 text-base" autoFocus autoComplete="username" autoCapitalize="none" value={username} onChange={(e) => setUsername(e.target.value)} />
            </Field>
            <Field label={t('auth.password')}>
              <input type="password" className="input py-2.5 text-base" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            {error && <p className="rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
            <button type="submit" className="btn-primary w-full py-2.5 text-base" disabled={busy || !username || !password}>
              {busy && <Spinner className="size-4" />} {t('auth.signIn')}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
