import { Link } from 'react-router-dom'
import { ExternalLink } from 'lucide-react'
import { useBusiness } from '../../lib/queries'
import { useI18n } from '../../i18n'
import { Profile } from '../../pages/BusinessPage'
import { Drawer, ErrorBlock, Loading } from '../ui'

/**
 * Everything about one business over the Main page: next steps with their times, every call, visit and
 * meeting, what they use and want, orders, contacts, extra fields. The list stays where it was underneath.
 */
export function BusinessDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { t } = useI18n()
  const business = useBusiness(id)
  return (
    <Drawer
      open={id !== null}
      onClose={onClose}
      title={business.data?.name ?? ''}
      actions={id !== null && (
        <Link to={`/businesses/${id}`} className="btn-ghost px-2 text-sm">
          <ExternalLink className="size-4" /> <span className="hidden sm:inline">{t('business.openFull')}</span>
        </Link>
      )}
    >
      {business.isLoading ? <Loading /> : business.error || !business.data ? (
        <ErrorBlock error={business.error} onRetry={() => business.refetch()} />
      ) : (
        <Profile key={business.data.id} b={business.data} backLabel="" embedded />
      )}
    </Drawer>
  )
}
