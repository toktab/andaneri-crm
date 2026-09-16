import { useEffect, useState } from 'react'
import { Share, Smartphone, X } from 'lucide-react'
import { isStandalone } from '../lib/push'
import { useIsPhone } from '../lib/mobile'
import { useI18n } from '../i18n'

interface InstallEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

const HIDDEN = 'andaneri.installHidden'

/**
 * Asks once to put the CRM on the Home Screen. Added that way it opens full screen without the browser
 * bars, remembers the sign-in, and - on an iPhone - is the only way reminders can arrive at all.
 * Android offers a real install prompt; iPhone has to be told where the button is.
 */
export function InstallPrompt() {
  const { t } = useI18n()
  const phone = useIsPhone()
  const [event, setEvent] = useState<InstallEvent | null>(null)
  const [hidden, setHidden] = useState(() => {
    try {
      return localStorage.getItem(HIDDEN) === '1'
    } catch {
      return false
    }
  })

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault()
      setEvent(e as InstallEvent)
    }
    window.addEventListener('beforeinstallprompt', onPrompt)
    return () => window.removeEventListener('beforeinstallprompt', onPrompt)
  }, [])

  if (!phone || hidden || isStandalone()) return null
  const iphone = /iPhone|iPad|iPod/.test(navigator.userAgent)
  if (!event && !iphone) return null

  const dismiss = () => {
    setHidden(true)
    try {
      localStorage.setItem(HIDDEN, '1')
    } catch {
      /* private window: it will ask again next time, which is fine */
    }
  }

  return (
    <section className="card mb-4 border-brand-200 bg-brand-50 p-3">
      <div className="flex items-start gap-3">
        <Smartphone className="mt-0.5 size-5 shrink-0 text-brand-600" />
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-semibold">{t('install.title')}</h2>
          <p className="mt-0.5 text-xs text-muted">{t('install.hint')}</p>
          {event ? (
            <button
              type="button"
              className="btn-primary mt-2.5 w-full"
              onClick={() => { void event.prompt().then(() => event.userChoice).then(dismiss) }}
            >
              {t('install.button')}
            </button>
          ) : (
            <ol className="mt-2 list-inside list-decimal space-y-1 text-xs text-ink/80">
              <li>{t('notify.iosStep1')} <Share className="mb-0.5 inline size-3.5" /></li>
              <li>{t('notify.iosStep2')}</li>
            </ol>
          )}
        </div>
        <button type="button" className="btn-ghost -mr-1 -mt-1 p-1.5" onClick={dismiss} aria-label={t('common.close')}>
          <X className="size-4" />
        </button>
      </div>
    </section>
  )
}
