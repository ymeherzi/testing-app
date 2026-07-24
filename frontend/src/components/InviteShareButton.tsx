import { useState } from 'react'
import { inviteLink } from '../lib/invite'
import { useT } from '../i18n'

export function InviteShareButton({ code, leagueName }: { code: string; leagueName: string }) {
  const t = useT()
  const [copied, setCopied] = useState(false)

  const share = async () => {
    const url = inviteLink(code)
    if (navigator.share) {
      try {
        await navigator.share({ title: t('leagues.shareTitle', { league: leagueName }), url })
        return
      } catch {
        // user cancelled the share sheet — fall through to clipboard
      }
    }
    await navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <button
      type="button"
      onClick={share}
      className="rounded-xl bg-emerald-500 px-4 py-2 text-sm font-semibold text-emerald-950 active:bg-emerald-400"
    >
      {copied ? t('leagues.linkCopied') : t('leagues.invite')}
    </button>
  )
}
