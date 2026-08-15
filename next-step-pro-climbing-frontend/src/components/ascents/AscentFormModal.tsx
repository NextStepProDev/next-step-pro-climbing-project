import { useId, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { StarRating } from './StarRating'
import { todayInWarsaw } from '../../utils/calendarDate'
import type {
  Ascent,
  AscentDiscipline,
  AscentOptions,
  AscentStyle,
  AscentTerrain,
  PlaceSuggestion,
  SaveAscent,
} from '../../types'

interface AscentFormModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (data: SaveAscent) => void
  saving: boolean
  error: string | null
  options: AscentOptions
  places: PlaceSuggestion[]
  terrain: AscentTerrain
  /** Present when correcting an existing entry. */
  editing?: Ascent | null
}

const INPUT_CLASS = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500/50'
const LABEL_CLASS = 'block text-sm font-medium text-surface-300 mb-1'

export function AscentFormModal({
  isOpen, onClose, onSubmit, saving, error, options, places, terrain, editing,
}: AscentFormModalProps) {
  const { t } = useTranslation('ascents')
  // Whether Escape or a backdrop click should ask first. Set from the form's own onChange —
  // a real user event, so no effect has to mirror the fields up here
  const [touched, setTouched] = useState(false)

  const close = () => {
    setTouched(false)
    onClose()
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={close}
      title={editing ? t('form.editTitle') : t('form.addTitle')}
      size="lg"
      confirmClose={touched && !saving}
    >
      {/* Mounted only while open — the form state resets on every open, with no effect to
          synchronise it (and no cascading render on the way in) */}
      {isOpen && (
        <AscentForm
          onClose={close}
          onTouched={() => setTouched(true)}
          onSubmit={onSubmit}
          saving={saving}
          error={error}
          options={options}
          places={places}
          terrain={terrain}
          editing={editing}
        />
      )}
    </Modal>
  )
}

function AscentForm({
  onClose, onTouched, onSubmit, saving, error, options, places, terrain, editing,
}: Omit<AscentFormModalProps, 'isOpen'> & { onTouched: () => void }) {
  const { t } = useTranslation('ascents')
  const fieldId = useId()

  const [climbedOn, setClimbedOn] = useState(editing?.climbedOn ?? todayInWarsaw)
  const [discipline, setDiscipline] = useState<AscentDiscipline>(editing?.discipline ?? 'SPORT')
  const [grade, setGrade] = useState(editing?.grade ?? '')
  const [style, setStyle] = useState<AscentStyle>(editing?.style ?? 'RP')
  const [area, setArea] = useState(editing?.area ?? '')
  const [crag, setCrag] = useState(editing?.crag ?? '')
  const [routeName, setRouteName] = useState(editing?.routeName ?? '')
  const [attempts, setAttempts] = useState(editing?.attempts?.toString() ?? '')
  const [qualityStars, setQualityStars] = useState<number | null>(editing?.qualityStars ?? null)
  const [comment, setComment] = useState(editing?.comment ?? '')
  const [gradeCleared, setGradeCleared] = useState(false)

  // ---- mountain-only state ----
  const isMountain = terrain === 'MOUNTAIN'
  const [winter, setWinter] = useState(editing?.winter ?? false)
  const [originalGrade, setOriginalGrade] = useState(editing?.originalGrade ?? '')
  const [lengthMeters, setLengthMeters] = useState(editing?.lengthMeters?.toString() ?? '')
  const [pitches, setPitches] = useState(editing?.pitches?.toString() ?? '')
  // Entered in hours, stored in minutes — hours are what anybody says out loud
  const [durationHours, setDurationHours] = useState(
    editing?.durationMinutes != null ? String(Math.round(editing.durationMinutes / 6) / 10) : '')
  const [ledGrade, setLedGrade] = useState(editing?.ledGrade ?? '')
  const [ledPitches, setLedPitches] = useState(editing?.ledPitches?.toString() ?? '')
  const [partners, setPartners] = useState(editing?.partners ?? '')

  const disciplineOption = useMemo(
    () => options.disciplines.find(option => option.value === discipline),
    [options.disciplines, discipline],
  )

  // Mountains use the unified French scale and every style applies there
  const grades = isMountain
    ? options.gradesByScale.FRENCH_ROUTE ?? []
    : (disciplineOption ? options.gradesByScale[disciplineOption.gradeScale] ?? [] : [])
  const styles = isMountain ? options.mountainStyles : (disciplineOption?.styles ?? [])

  /**
   * Changing the discipline changes the scale, so a grade from the other one is CLEARED, never
   * converted. A French-to-Font conversion table is guesswork, and quietly turning 7a into 6C
   * would credit the athlete with an ascent they never made.
   */
  const changeDiscipline = (next: AscentDiscipline) => {
    const nextOption = options.disciplines.find(option => option.value === next)
    setDiscipline(next)
    const gradeStillValid = grade !== '' && nextOption
      && (options.gradesByScale[nextOption.gradeScale] ?? []).some(option => option.value === grade)
    if (!gradeStillValid && grade !== '') {
      setGrade('')
      setGradeCleared(true)
    }
    if (nextOption && !nextOption.styles.includes(style)) {
      setStyle(nextOption.styles.includes('RP') ? 'RP' : nextOption.styles[0])
    }
  }

  // OS and FLASH mean "first go" by definition, so there is nothing to count
  const showsAttempts = style !== 'OS' && style !== 'FLASH'

  const cragOptions = useMemo(() => {
    const forArea = places.find(place => place.area.toLowerCase() === area.trim().toLowerCase())
    // Before an area is chosen, offer every crag rather than nothing — the athlete may well
    // start from the crag name and fill the area in afterwards
    return forArea ? forArea.crags : [...new Set(places.flatMap(place => place.crags))]
  }, [places, area])

  const complete = climbedOn !== '' && grade !== '' && area.trim() !== ''
    && crag.trim() !== '' && routeName.trim() !== ''

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!complete || saving) return

    const parsedAttempts = showsAttempts && attempts.trim() !== '' ? Number(attempts) : null
    const num = (value: string): number | null => {
      const parsed = Number(value.replace(',', '.'))
      return value.trim() !== '' && Number.isFinite(parsed) ? parsed : null
    }
    const hours = num(durationHours)

    onSubmit({
      terrain,
      climbedOn,
      discipline: isMountain ? null : discipline,
      grade,
      style,
      area: area.trim(),
      crag: crag.trim(),
      routeName: routeName.trim(),
      attempts: isMountain
        ? null
        : (parsedAttempts !== null && Number.isFinite(parsedAttempts) ? parsedAttempts : null),
      qualityStars: isMountain ? null : qualityStars,
      comment: comment.trim() === '' ? null : comment.trim(),
      winter: isMountain ? winter : null,
      originalGrade: isMountain && originalGrade.trim() !== '' ? originalGrade.trim() : null,
      lengthMeters: isMountain ? num(lengthMeters) : null,
      pitches: isMountain ? num(pitches) : null,
      durationMinutes: isMountain && hours !== null ? Math.round(hours * 60) : null,
      ledGrade: isMountain && ledGrade !== '' ? ledGrade : null,
      ledPitches: isMountain ? num(ledPitches) : null,
      partners: isMountain && partners.trim() !== '' ? partners.trim() : null,
    })
  }

  return (
    // change events bubble, so one handler here marks the whole form touched — no per-field
    // bookkeeping, and losing a typed-out ascent to a stray Escape stays impossible
    <form onSubmit={handleSubmit} onChange={onTouched} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className={LABEL_CLASS} htmlFor={`${fieldId}-date`}>{t('form.date')}</label>
          <input
            id={`${fieldId}-date`}
            type="date"
            value={climbedOn}
            // Warsaw's today, not the device's: the backend refuses a future date by Warsaw
            max={todayInWarsaw()}
            onChange={event => setClimbedOn(event.target.value)}
            className={INPUT_CLASS}
            required
          />
        </div>

        {isMountain ? (
          <div>
            <label className={LABEL_CLASS} htmlFor={`${fieldId}-season`}>{t('form.season')}</label>
            <select
              id={`${fieldId}-season`}
              value={winter ? 'winter' : 'summer'}
              onChange={event => setWinter(event.target.value === 'winter')}
              className={INPUT_CLASS}
            >
              <option value="summer">{t('season.summer')}</option>
              <option value="winter">{t('season.winter')}</option>
            </select>
          </div>
        ) : (
          <div>
            <label className={LABEL_CLASS} htmlFor={`${fieldId}-discipline`}>{t('form.discipline')}</label>
            <select
              id={`${fieldId}-discipline`}
              value={discipline}
              onChange={event => changeDiscipline(event.target.value as AscentDiscipline)}
              className={INPUT_CLASS}
            >
              {options.disciplines.map(option => (
                <option key={option.value} value={option.value}>
                  {t(`discipline.${option.value}`)}
                </option>
              ))}
            </select>
          </div>
        )}

        <div>
          <label className={LABEL_CLASS} htmlFor={`${fieldId}-grade`}>{t('form.grade')}</label>
          <select
            id={`${fieldId}-grade`}
            value={grade}
            onChange={event => { setGrade(event.target.value); setGradeCleared(false) }}
            className={INPUT_CLASS}
            required
          >
            <option value="">{t('form.gradePlaceholder')}</option>
            {/* Hardest first: the grade being logged is usually near the top of what one climbs */}
            {[...grades].reverse().map(option => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          {gradeCleared && (
            <p className="mt-1 text-xs text-amber-400/90">{t('form.gradeCleared')}</p>
          )}
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor={`${fieldId}-style`}>{t('form.style')}</label>
          <select
            id={`${fieldId}-style`}
            value={style}
            onChange={event => setStyle(event.target.value as AscentStyle)}
            className={INPUT_CLASS}
          >
            {/* Styles come from the server's catalogue: bouldering has no toprope, the
                mountains have no toprope either, and pinkpoint no longer exists anywhere */}
            {styles.map(option => (
              <option key={option} value={option}>{t(`style.${option}`)}</option>
            ))}
          </select>
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor={`${fieldId}-area`}>{t('form.area')}</label>
          <input
            id={`${fieldId}-area`}
            list={`${fieldId}-areas`}
            value={area}
            onChange={event => setArea(event.target.value)}
            placeholder={t(isMountain ? 'form.areaPlaceholderMountain' : 'form.areaPlaceholder')}
            maxLength={120}
            className={INPUT_CLASS}
            required
          />
          {/* Suggestions come from the athlete's own history, so the second entry for a crag
              is made by picking rather than retyping — that is what keeps the grouping honest */}
          <datalist id={`${fieldId}-areas`}>
            {places.map(place => <option key={place.area} value={place.area} />)}
          </datalist>
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor={`${fieldId}-crag`}>{isMountain ? t('form.summit') : t('form.crag')}</label>
          <input
            id={`${fieldId}-crag`}
            list={`${fieldId}-crags`}
            value={crag}
            onChange={event => setCrag(event.target.value)}
            placeholder={isMountain ? t('form.summitPlaceholder') : t('form.cragPlaceholder')}
            maxLength={120}
            className={INPUT_CLASS}
            required
          />
          <datalist id={`${fieldId}-crags`}>
            {cragOptions.map(option => <option key={option} value={option} />)}
          </datalist>
        </div>
      </div>

      <div>
        <label className={LABEL_CLASS} htmlFor={`${fieldId}-route`}>{t('form.route')}</label>
        <input
          id={`${fieldId}-route`}
          value={routeName}
          onChange={event => setRouteName(event.target.value)}
          maxLength={160}
          className={INPUT_CLASS}
          required
        />
      </div>

      {isMountain && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-original`}>{t('form.originalGrade')}</label>
              <input id={`${fieldId}-original`} value={originalGrade} maxLength={40}
                     onChange={e => setOriginalGrade(e.target.value)}
                     placeholder={t('form.originalGradePlaceholder')} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-length`}>{t('form.lengthMeters')}</label>
              <input id={`${fieldId}-length`} type="number" min={1} max={4000} value={lengthMeters}
                     onChange={e => setLengthMeters(e.target.value)} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-pitches`}>{t('form.pitches')}</label>
              <input id={`${fieldId}-pitches`} type="number" min={1} max={60} value={pitches}
                     onChange={e => setPitches(e.target.value)} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-duration`}>{t('form.durationHours')}</label>
              <input id={`${fieldId}-duration`} inputMode="decimal" value={durationHours}
                     onChange={e => setDurationHours(e.target.value)}
                     placeholder="6,5" className={INPUT_CLASS} />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-led`}>{t('form.ledGrade')}</label>
              <select id={`${fieldId}-led`} value={ledGrade} onChange={e => setLedGrade(e.target.value)}
                      className={INPUT_CLASS}>
                <option value="">{t('form.ledGradeNone')}</option>
                {[...grades].reverse().map(option => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <p className="mt-1 text-xs text-surface-500">{t('form.ledGradeHint')}</p>
            </div>
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-ledPitches`}>{t('form.ledPitches')}</label>
              <input id={`${fieldId}-ledPitches`} type="number" min={0} max={60} value={ledPitches}
                     onChange={e => setLedPitches(e.target.value)} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS} htmlFor={`${fieldId}-partners`}>{t('form.partners')}</label>
              <input id={`${fieldId}-partners`} value={partners} maxLength={300}
                     onChange={e => setPartners(e.target.value)} className={INPUT_CLASS} />
            </div>
          </div>
        </>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {!isMountain && showsAttempts && (
          <div>
            <label className={LABEL_CLASS} htmlFor={`${fieldId}-attempts`}>{t('form.attempts')}</label>
            <input
              id={`${fieldId}-attempts`}
              type="number"
              inputMode="numeric"
              min={1}
              max={9999}
              value={attempts}
              onChange={event => setAttempts(event.target.value)}
              className={INPUT_CLASS}
            />
            <p className="mt-1 text-xs text-surface-500">{t('form.attemptsHint')}</p>
          </div>
        )}

        <div>
          <span className={LABEL_CLASS}>{t('form.stars')}</span>
          <StarRating value={qualityStars} onChange={setQualityStars} />
        </div>
      </div>

      <div>
        <label className={LABEL_CLASS} htmlFor={`${fieldId}-comment`}>{t('form.comment')}</label>
        <textarea
          id={`${fieldId}-comment`}
          value={comment}
          onChange={event => setComment(event.target.value)}
          rows={3}
          maxLength={2000}
          placeholder={t('form.commentPlaceholder')}
          className={INPUT_CLASS}
        />
      </div>

      {error && <p className="text-sm text-rose-400/90">{error}</p>}

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="ghost" onClick={onClose} disabled={saving}>
          {t('form.cancel')}
        </Button>
        <Button type="submit" loading={saving} disabled={!complete}>
          {saving ? t('form.saving') : t('form.save')}
        </Button>
      </div>
    </form>
  )
}
