import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTeams, useUpdateProfile } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { countries } from '../lib/countries'
import { LOCALES, useI18n, type Locale } from '../i18n'

export function ProfilePage() {
  const { user, logout, updateUser } = useAuth()
  const { t, locale, setLocale } = useI18n()
  const { data: teams } = useTeams()
  const updateProfile = useUpdateProfile()
  const navigate = useNavigate()

  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [country, setCountry] = useState(user?.country ?? '')
  const [clubId, setClubId] = useState(user?.favouriteClubTeamId?.toString() ?? '')
  const [message, setMessage] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setMessage(null)
    try {
      const updated = await updateProfile.mutateAsync({
        displayName,
        country: country || null,
        favouriteClubTeamId: clubId ? Number(clubId) : null,
      })
      updateUser(updated)
      setMessage(t('profile.saved'))
    } catch (e) {
      setMessage(e instanceof Error ? e.message : t('profile.saveFailed'))
    }
  }

  const inputClass =
    'w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500'

  return (
    <div className="space-y-6 p-4">
      <header className="pt-2">
        <h1 className="text-xl font-bold">{t('profile.title')}</h1>
        <p className="mt-1 text-sm text-slate-400">{user?.email}</p>
      </header>

      <form onSubmit={submit} className="space-y-4">
        <label className="block space-y-1">
          <span className="text-sm text-slate-400">{t('profile.displayName')}</span>
          <input type="text" required minLength={2} maxLength={50} value={displayName}
                 onChange={(e) => setDisplayName(e.target.value)} className={inputClass} />
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-slate-400">{t('profile.country')}</span>
          <select value={country} onChange={(e) => setCountry(e.target.value)} className={inputClass}>
            <option value="">{t('profile.notSet')}</option>
            {countries().map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-slate-400">{t('profile.club')}</span>
          <select value={clubId} onChange={(e) => setClubId(e.target.value)} className={inputClass}>
            <option value="">{t('profile.notSet')}</option>
            {teams?.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-slate-400">{t('profile.language')}</span>
          <select value={locale} onChange={(e) => setLocale(e.target.value as Locale)} className={inputClass}>
            {Object.entries(LOCALES).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </label>
        {message && <p className="text-sm text-slate-300">{message}</p>}
        <button
          type="submit"
          disabled={updateProfile.isPending}
          className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
        >
          {updateProfile.isPending ? t('profile.saving') : t('profile.save')}
        </button>
      </form>

      <button
        type="button"
        onClick={() => {
          logout()
          navigate('/login')
        }}
        className="w-full rounded-xl border border-slate-700 py-3 font-semibold text-slate-300 active:bg-slate-900"
      >
        {t('auth.signOut')}
      </button>
    </div>
  )
}
