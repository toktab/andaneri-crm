import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { fmtDate } from '../lib/format'
import { useI18n } from '../i18n'
import { Spinner } from './ui'
import { useToast } from './Toast'

interface Status { lastBackupAt: string | null; due: boolean; reminder: boolean; intervalDays: number }
const LATER = 'andaneri.backup.later'

/**
 * Asks admins for a backup when the last one is older than the backup interval (a week by default):
 * one tap makes it and downloads it. "Later" hides the reminder until the browser is closed.
 */
export function BackupReminder() {
  const { t, lang } = useI18n()
  const { isAdmin } = useAuth()
  const toast = useToast()
  const client = useQueryClient()
  const [later, setLater] = useState(() => {
    try {
      return sessionStorage.getItem(LATER) === '1'
    } catch {
      return false
    }
  })
  const [busy, setBusy] = useState(false)
  const status = useQuery({
    queryKey: ['backup-status'],
    queryFn: () => api.get<Status>('/admin/backups/status'),
    enabled: isAdmin,
    staleTime: 10 * 60_000,
    refetchInterval: 60 * 60_000,
  })

  if (!isAdmin || later || !status.data?.due || !status.data.reminder) return null

  const backupNow = async () => {
    setBusy(true)
    try {
      const backup = await api.post<{ id: number; fileName: string }>('/admin/backups')
      await api.download(`/admin/backups/${backup.id}/download`, undefined, backup.fileName)
      toast.ok(t('admin.backupDone'))
      await client.invalidateQueries({ queryKey: ['backup-status'] })
      await client.invalidateQueries({ queryKey: ['backups'] })
    } catch (error) {
      toast.error(error)
    } finally {
      setBusy(false)
    }
  }
  const postpone = () => {
    setLater(true)
    try {
      sessionStorage.setItem(LATER, '1')
    } catch {
      /* hidden until reload */
    }
  }

  return (
    <div className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 bg-sky-100 px-4 py-1.5 text-xs text-sky-900">
      <Download className="size-3.5" />
      <span>{status.data.lastBackupAt ? t('admin.backupDueSince', { date: fmtDate(status.data.lastBackupAt, lang) }) : t('admin.backupNever')}</span>
      <button type="button" className="font-semibold underline" disabled={busy} onClick={() => void backupNow()}>
        {busy ? <Spinner className="inline size-3.5" /> : t('admin.backupNow')}
      </button>
      <button type="button" className="opacity-70 hover:opacity-100" onClick={postpone}>{t('admin.later')}</button>
    </div>
  )
}
