// Putting a task into the phone's own calendar app, with its reminder.

import { api } from './api'
import type { TaskDto } from './types'

/**
 * Opens the task as a calendar file: iPhone and Mac show "Add to Calendar" with the alert already set,
 * Android and Windows open it in their calendar app. The address carries the person's calendar token,
 * because calendar apps cannot send the sign-in.
 */
export async function openInCalendar(taskId: number, lang: string): Promise<void> {
  const { path } = await api.post<{ path: string }>(`/tasks/${taskId}/calendar-link`, undefined, { lang })
  window.location.href = path
}

/** The "Add to Google Calendar" page for a task, for Android phones that live in Google Calendar. */
export function googleCalendarUrl(task: TaskDto, label: string): string {
  const start = new Date(task.dueAt)
  const minutes = task.type === 'MEETING' || task.type === 'VISIT' ? 60 : 30
  const end = task.endAt ? new Date(task.endAt) : new Date(start.getTime() + minutes * 60_000)
  const stamp = (d: Date) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
  const day = (d: Date) => `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  const dates = task.allDay ? `${day(start)}/${day(new Date(start.getTime() + 86_400_000))}` : `${stamp(start)}/${stamp(end)}`
  const details = [
    task.businessName ? task.title : null,
    task.contact ? [task.contact.name, task.contact.phone].filter(Boolean).join(' ') : null,
    task.businessPhone,
    task.notes,
    task.businessId ? `${window.location.origin}/main?open=${task.businessId}` : null,
  ].filter(Boolean).join('\n')
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: `${label}: ${task.businessName ?? task.title ?? ''}`,
    dates,
    details,
    location: task.location ?? task.businessAddress ?? '',
  })
  return `https://calendar.google.com/calendar/render?${params.toString()}`
}
