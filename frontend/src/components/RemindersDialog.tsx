import { useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { BellOff, BellRing, CalendarPlus, CircleAlert, CircleCheck, Copy, RefreshCw, Send, Share, Smartphone, SquarePlus } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { currentSubscription, disablePush, enablePush, isAndroid, isIos, isStandalone, pushOnHere, pushSupported,
  type Delivery, type PushStatus, type TestResult } from '../lib/push'
import { fmtDateTime } from '../lib/format'
import { useI18n } from '../i18n'
import { Modal, Spinner } from './ui'
import { useToast } from './Toast'

const MINUTES = [0, 15, 30, 60, 120, 1440]

/**
 * The three ways to be reminded, side by side so the team can try each: push notifications on this device,
 * a calendar feed for the phone's calendar app, and (on every meeting) "Add to calendar".
 */
export function RemindersDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, lang } = useI18n()
  const toast = useToast()
  const client = useQueryClient()
  // The address of this very device, so the list can mark which line is the phone in your hand.
  const mine = useQuery({
    queryKey: ['push-endpoint'],
    queryFn: async () => (await currentSubscription())?.endpoint ?? '',
    enabled: open,
  })
  const status = useQuery({
    queryKey: ['push-status', mine.data ?? ''],
    queryFn: () => api.get<PushStatus>('/push/status', mine.data ? { endpoint: mine.data } : undefined),
    enabled: open && mine.isFetched,
  })
  const feed = useQuery({ queryKey: ['calendar-feed'], queryFn: () => api.get<{ token: string | null }>('/me/calendar'), enabled: open })
  const [here, setHere] = useState(pushOnHere)
  const [busy, setBusy] = useState<string | null>(null)
  const [tested, setTested] = useState<Delivery[] | null>(null)

  const supported = pushSupported()
  const needsInstall = isIos() && !isStandalone()
  const denied = typeof Notification !== 'undefined' && Notification.permission === 'denied'

  const run = async (key: string, action: () => Promise<unknown>) => {
    setBusy(key)
    try {
      await action()
    } catch (error) {
      const code = error instanceof Error && (error.message === 'DENIED' || error.message === 'NOT_ASKED')
        ? (error.message === 'NOT_ASKED' ? 'PUSH_NOT_ASKED' : 'PUSH_DENIED')
        : null
      toast.error(code ? new ApiError(0, { code }) : error)
    } finally {
      setBusy(null)
    }
  }

  const togglePush = () => run('push', async () => {
    if (here) {
      await disablePush()
      setHere(false)
    } else {
      await enablePush(lang)
      setHere(true)
      toast.ok(t('notify.enabled'))
    }
    await client.invalidateQueries({ queryKey: ['push-status'] })
  })

  const sendTest = () => run('test', async () => {
    const result = await api.post<TestResult>('/push/test')
    setTested(result.devices)
    await client.invalidateQueries({ queryKey: ['push-status'] })
    if (result.sent > 0) toast.ok(t('notify.testSent', { n: result.sent }))
    else if (result.devices.length === 0) toast.error(new ApiError(0, { code: 'NO_DEVICES' }))
    // Refused by the push service: the reason is on the screen now, not only in the server log.
    else toast.error(new ApiError(0, { code: 'PUSH_REFUSED' }))
  })

  const setMinutes = (minutes: number) => run('minutes', async () => {
    await api.put('/me/reminders', { reminderMinutes: minutes })
    await client.invalidateQueries({ queryKey: ['push-status'] })
    toast.ok(t('common.saved'))
  })

  const feedUrl = feed.data?.token ? `${window.location.origin}/cal/${feed.data.token}.ics${lang === 'en' ? '?lang=en' : ''}` : null
  const webcal = feedUrl?.replace(/^https?:/, 'webcal:')

  const newFeed = (confirmFirst: boolean) => run('feed', async () => {
    if (confirmFirst && !window.confirm(t('notify.feedRegenerateConfirm'))) return
    await api.post('/me/calendar')
    await client.invalidateQueries({ queryKey: ['calendar-feed'] })
  })
  const feedOff = () => run('feed', async () => {
    await api.del('/me/calendar')
    await client.invalidateQueries({ queryKey: ['calendar-feed'] })
  })
  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      toast.ok(t('notify.copied'))
    } catch {
      window.prompt(t('notify.copyManually'), text)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('notify.title')} wide>
      <div className="space-y-5">
        {/* When */}
        <Section icon={<BellRing className="size-5" />} title={t('notify.whenTitle')}>
          <p className="mb-2 text-sm text-muted">{t('notify.whenHint')}</p>
          <div className="flex flex-wrap gap-1.5">
            {MINUTES.map((m) => (
              <button key={m} type="button" disabled={busy === 'minutes' || !status.data}
                className={status.data?.reminderMinutes === m ? 'chip-on' : 'chip-off'} onClick={() => void setMinutes(m)}>
                {minutesLabel(m, t)}
              </button>
            ))}
          </div>
        </Section>

        {/* 1. Push */}
        <Section icon={<Smartphone className="size-5" />} title={t('notify.pushTitle')} badge={here ? t('notify.onHere') : undefined}>
          <p className="mb-3 text-sm text-muted">{t('notify.pushHint')}</p>
          {needsInstall ? (
            <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
              <b className="block">{t('notify.iosTitle')}</b>
              <ol className="mt-1.5 list-decimal space-y-1 pl-5">
                <li className="flex-wrap">{t('notify.iosStep1')} <Share className="mb-0.5 inline size-4" /></li>
                <li>{t('notify.iosStep2')} <SquarePlus className="mb-0.5 inline size-4" /></li>
                <li>{t('notify.iosStep3')}</li>
              </ol>
            </div>
          ) : !supported ? (
            <p className="rounded-xl bg-canvas p-3 text-sm">{t('notify.unsupported')}</p>
          ) : (
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" className={here ? 'btn-secondary' : 'btn-primary'} disabled={busy !== null || (denied && !here)} onClick={() => void togglePush()}>
                {busy === 'push' ? <Spinner className="size-4" /> : here ? <BellOff className="size-4" /> : <BellRing className="size-4" />}
                {here ? t('notify.turnOff') : t('notify.turnOn')}
              </button>
              <button type="button" className="btn-secondary" disabled={busy !== null || !status.data?.devices} onClick={() => void sendTest()}>
                {busy === 'test' ? <Spinner className="size-4" /> : <Send className="size-4" />} {t('notify.test')}
              </button>
              <span className="text-xs text-muted">{t('notify.devices', { n: status.data?.devices ?? 0 })}</span>
              {denied && <p className="w-full text-sm text-rose-700">{t('notify.deniedHelp')}</p>}
              {/* Android phones shut background work down to save battery; this is where that is undone. */}
              {here && isAndroid() && <p className="w-full rounded-xl bg-canvas p-2.5 text-xs text-muted">{t('notify.androidHint')}</p>}

              {/* Which devices are switched on, and what the push service last answered to each. */}
              {(status.data?.deviceList ?? []).length > 0 && (
                <ul className="w-full space-y-1.5 border-t border-line pt-2.5">
                  {(status.data?.deviceList ?? []).map((device) => {
                    const result = tested?.find((x) => x.deviceId === device.id)
                    const failed = result ? !result.accepted : Boolean(device.lastError)
                    return (
                      <li key={device.id} className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs">
                        {failed
                          ? <CircleAlert className="size-4 shrink-0 text-rose-600" />
                          : <CircleCheck className={`size-4 shrink-0 ${result || device.lastSuccessAt ? 'text-emerald-600' : 'text-muted'}`} />}
                        <span className="font-medium">{device.name}</span>
                        {device.thisDevice && <span className="chip border-brand-200 bg-brand-50 text-brand-800">{t('notify.thisDevice')}</span>}
                        {device.lastSuccessAt && !failed && (
                          <span className="text-muted">{t('notify.lastDelivered', { at: fmtDateTime(device.lastSuccessAt, lang) })}</span>
                        )}
                        {failed && (
                          <span className="w-full text-rose-700">
                            {t('notify.refusedBy', { status: String(result?.status ?? device.lastStatus ?? '-') })}
                            {(result?.error ?? device.lastError) ? `: ${result?.error ?? device.lastError}` : ''}
                          </span>
                        )}
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          )}
        </Section>

        {/* 2. Calendar feed */}
        <Section icon={<RefreshCw className="size-5" />} title={t('notify.feedTitle')} badge={feedUrl ? t('notify.feedOn') : undefined}>
          <p className="mb-3 text-sm text-muted">{t('notify.feedHint')}</p>
          {feedUrl ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <a href={webcal} className="btn-primary"><CalendarPlus className="size-4" /> {t('notify.feedOpen')}</a>
                <button type="button" className="btn-secondary" onClick={() => void copy(feedUrl)}><Copy className="size-4" /> {t('notify.feedCopy')}</button>
                <button type="button" className="btn-ghost text-sm" disabled={busy !== null} onClick={() => void newFeed(true)}>{t('notify.feedRegenerate')}</button>
                <button type="button" className="btn-ghost text-sm text-rose-700" disabled={busy !== null} onClick={() => void feedOff()}>{t('notify.feedOff')}</button>
              </div>
              <code className="block break-all rounded-lg bg-canvas px-2 py-1.5 text-xs">{feedUrl}</code>
              <ul className="space-y-1.5 text-sm">
                <li><b>iPhone:</b> {t('notify.feedIphone')}</li>
                <li><b>Android / Google:</b> {t('notify.feedGoogle')}</li>
                <li><b>Outlook / Mac:</b> {t('notify.feedDesktop')}</li>
              </ul>
            </div>
          ) : (
            <button type="button" className="btn-secondary" disabled={busy !== null || feed.isLoading} onClick={() => void newFeed(false)}>
              {busy === 'feed' ? <Spinner className="size-4" /> : <RefreshCw className="size-4" />} {t('notify.feedTurnOn')}
            </button>
          )}
        </Section>

        {/* 3. One meeting at a time */}
        <Section icon={<CalendarPlus className="size-5" />} title={t('notify.addTitle')}>
          <p className="text-sm text-muted">{t('notify.addHint')}</p>
        </Section>

        <p className="rounded-xl bg-canvas p-3 text-xs text-muted">{t('notify.doubleNote')}</p>
      </div>
    </Modal>
  )
}

function Section({ icon, title, badge, children }: { icon: ReactNode; title: string; badge?: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line p-3.5">
      <h3 className="mb-1.5 flex items-center gap-2 text-sm font-semibold">
        <span className="text-brand-600">{icon}</span> {title}
        {badge && <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-medium text-emerald-800">{badge}</span>}
      </h3>
      {children}
    </section>
  )
}

export function minutesLabel(minutes: number, t: (key: string, vars?: Record<string, string | number>) => string): string {
  if (minutes === 0) return t('notify.none')
  if (minutes % 1440 === 0) return t('notify.days', { n: minutes / 1440 })
  if (minutes % 60 === 0) return t('notify.hours', { n: minutes / 60 })
  return t('notify.minutes', { n: minutes })
}
