import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { format } from "date-fns";
import { ArrowLeft, Clock, Calendar, CalendarPlus, Users, Plus, ExternalLink, X, Phone } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import clsx from "clsx";
import type { TimeSlot, EventSummary } from "../../types";
import { formatAvailability, getEventColorByIndex } from "../../utils/events";
import { useDateLocale } from "../../utils/dateFnsLocale";
import { useAuth } from "../../context/AuthContext";

interface DayViewProps {
  date: string;
  slots: TimeSlot[];
  events: EventSummary[];
  onBack: () => void;
  onSlotClick: (slotId: string) => void;
  onEventClick?: (event: EventSummary) => void;
  onCancelEvent?: (eventId: string) => void;
  onAddSlot?: () => void;
  /** Training request (non-admin): shown on an empty day. */
  onProposeTraining?: () => void;
}

/* ===============================
   Slot button
   =============================== */
function SlotButton({
  slot,
  onSlotClick,
  showTitle = false,
}: {
  slot: TimeSlot;
  onSlotClick: (slotId: string) => void;
  showTitle?: boolean;
}) {
  const { t } = useTranslation('calendar');
  const { isAdmin, isAuthenticated } = useAuth();

  const isAvailabilityWindow = slot.status === "AVAILABILITY_WINDOW";
  const isDisabled = !isAdmin && (slot.status === "BLOCKED" || slot.status === "PAST");
  // "By invitation" is shown ONLY to anonymous users — an unrecognized invitee may be among them.
  // A logged-in non-invitee sees a plain "full" (no need to know about invitations).
  const invitedOnly = !isAuthenticated && slot.status === "FULL" && slot.reservedSeats > 0 && !slot.isReservedForUser && !slot.isUserRegistered;

  return (
    <button
      onClick={() => onSlotClick(slot.id)}
      disabled={isDisabled}
      className={clsx(
        "w-full p-4 rounded-lg border transition-all text-left",
        slot.status === "AVAILABLE" &&
          "border-green-500/40 hover:border-green-500 hover:bg-surface-800",
        slot.status === "FULL" && !invitedOnly &&
          "border-surface-700 hover:border-amber-500 hover:bg-surface-800",
        slot.status === "FULL" && invitedOnly &&
          "border-violet-500/40 hover:border-violet-500 hover:bg-surface-800",
        slot.status === "BLOCKED" && !isAdmin &&
          "border-surface-800 bg-surface-900/50 cursor-not-allowed opacity-60",
        slot.status === "BLOCKED" && isAdmin &&
          "border-surface-800 bg-surface-900/50 opacity-60 hover:opacity-80 hover:border-rose-500/50",
        slot.status === "PAST" && !isAdmin &&
          "border-surface-800 bg-surface-900/50 cursor-not-allowed opacity-50",
        slot.status === "PAST" && isAdmin &&
          "border-surface-800 bg-surface-900/50 opacity-50 hover:opacity-70 hover:border-rose-500/50",
        slot.status === "BOOKING_CLOSED" &&
          "border-surface-700 hover:border-amber-500 hover:bg-surface-800",
        isAvailabilityWindow &&
          "border-teal-500/50 bg-teal-500/5 hover:bg-teal-500/10",
        slot.isUserRegistered && "border-primary-500 bg-primary-500/10",
      )}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          {isAvailabilityWindow
            ? <Phone className="w-5 h-5 text-teal-400" />
            : <Clock className="w-5 h-5 text-surface-400" />
          }
          <div>
            <span className={clsx("font-medium", isAvailabilityWindow ? "text-teal-300" : "text-surface-100")}>
              {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
            </span>
            {showTitle && slot.eventTitle && (
              <p className="text-sm text-surface-300 mt-0.5">{slot.eventTitle}</p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {isAvailabilityWindow && (
            <span className="px-2 py-1 text-xs font-medium bg-teal-500/20 text-teal-400 rounded">
              {t('day.callToBook')}
            </span>
          )}
          {!isAvailabilityWindow && slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-primary-500/20 text-primary-400 rounded">
              {t('day.yourReservation')}
            </span>
          )}
          {slot.status === "AVAILABLE" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-green-500/15 text-green-300 rounded">
              {t('day.available')}
            </span>
          )}
          {slot.status === "FULL" && !slot.isUserRegistered && invitedOnly && (
            <div className="flex flex-col items-end gap-0.5">
              <span className="px-2 py-1 text-xs font-medium bg-violet-500/15 text-violet-200 rounded">
                {t('day.invitedOnly')}
              </span>
              <span className="text-xs text-violet-300/80 pr-1">
                {t('day.invitedLoginHint')}
              </span>
            </div>
          )}
          {slot.status === "FULL" && !slot.isUserRegistered && !invitedOnly && (
            <div className="flex flex-col items-end gap-0.5">
              <span className="px-2 py-1 text-xs font-medium bg-amber-500/10 text-amber-400 rounded">
                {t('day.full')}
              </span>
              <span className="text-xs text-amber-300/70 pr-1">
                {t('day.waitlistHint')}
              </span>
            </div>
          )}
          {slot.status === "BLOCKED" && (
            <span className="px-2 py-1 text-xs font-medium bg-surface-700 text-surface-400 rounded">
              {t('day.blocked')}
            </span>
          )}
          {slot.status === "BOOKING_CLOSED" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-amber-500/10 text-amber-400 rounded">
              {t('day.bookingClosed')}
            </span>
          )}
          {slot.status === "PAST" && (
            <span className="px-2 py-1 text-xs font-medium bg-surface-700 text-surface-500 rounded">
              {t('day.past')}
            </span>
          )}
        </div>
      </div>
    </button>
  );
}

/* ===============================
   DayView
   =============================== */
export function DayView({
  date,
  slots,
  events,
  onBack,
  onSlotClick,
  onEventClick,
  onCancelEvent,
  onAddSlot,
  onProposeTraining,
}: DayViewProps) {
  const { t } = useTranslation('calendar');
  const locale = useDateLocale();
  const location = useLocation();
  // Back-link target so CourseDetailPage returns here instead of the course list.
  const courseReturnTo = location.pathname + location.search;
  const dateObj = new Date(date);

  const { eventSlotGroups, standaloneSlots } = useMemo(() => {
    const grouped = new Map<string, TimeSlot[]>();
    const standalone: TimeSlot[] = [];

    for (const slot of slots) {
      if (slot.eventTitle) {
        const existing = grouped.get(slot.eventTitle);
        if (existing) {
          existing.push(slot);
        } else {
          grouped.set(slot.eventTitle, [slot]);
        }
      } else {
        standalone.push(slot);
      }
    }

    const eventTitles = new Set(events.map((e) => e.title));
    for (const [title, groupSlots] of grouped) {
      if (!eventTitles.has(title)) {
        standalone.push(...groupSlots);
        grouped.delete(title);
      }
    }

    for (const [, groupSlots] of grouped) {
      groupSlots.sort((a, b) => a.startTime.localeCompare(b.startTime));
    }

    standalone.sort((a, b) => a.startTime.localeCompare(b.startTime));

    return { eventSlotGroups: grouped, standaloneSlots: standalone };
  }, [slots, events]);

  const hasAnyContent = slots.length > 0 || events.length > 0;

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-4 p-4 border-b border-surface-800">
        <button
          aria-label={t('day.back')}
          onClick={onBack}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <h2 className="text-lg font-semibold text-surface-100 capitalize flex-1">
          {format(dateObj, "EEEE, d MMMM yyyy", { locale })}
        </h2>

        {onAddSlot && (
          <button
            onClick={onAddSlot}
            title={t('createSlot.title')}
            className="p-2 text-surface-400 hover:text-primary-400 hover:bg-surface-800 rounded-lg transition-colors"
          >
            <Plus className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Content */}
      <div className="p-4 space-y-6">
        {!hasAnyContent ? (
          <div className="text-center py-8 text-surface-400">
            <p>{t('day.noSlots')}</p>
            {onProposeTraining && (
              <button
                onClick={onProposeTraining}
                className="inline-flex items-center gap-2 mt-4 px-4 py-2 rounded-lg bg-primary-600 hover:bg-primary-500 text-white text-sm font-medium transition-colors"
              >
                <CalendarPlus className="w-4 h-4" />
                {t('day.proposeTraining')}
              </button>
            )}
          </div>
        ) : (
          <>
            {/* Event sections */}
            {events.map((event) => {
              const eventSlots = eventSlotGroups.get(event.title);
              const { label, badgeClass } = formatAvailability(event);
              const color = getEventColorByIndex(event.id, event.eventType, event.currentParticipants >= event.maxParticipants);

              /* UNAVAILABLE — instructor absence / blocked day, no enrollment */
              if (event.eventType === 'UNAVAILABLE') {
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <h3 className={clsx("text-base font-semibold mb-2", color.text)}>
                      {event.title}
                    </h3>
                    {event.description && (
                      <p className="text-sm text-surface-300 mb-3">{event.description}</p>
                    )}
                    <div className="p-3 rounded-lg bg-slate-500/10 border border-slate-500/20">
                      <p className="text-sm text-slate-300">{t('unavailable.message')}</p>
                    </div>
                  </div>
                );
              }

              /* CONTACT_DAY — no enrollment, just info + contact prompt */
              if (event.eventType === 'CONTACT_DAY') {
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <h3 className={clsx("text-base font-semibold mb-2", color.text)}>
                      {event.title}
                    </h3>
                    {event.description && (
                      <p className="text-sm text-surface-300 mb-3">{event.description}</p>
                    )}
                    <div className="p-3 rounded-lg bg-indigo-500/10 border border-indigo-500/20">
                      <p className="text-sm text-indigo-300">{t('contactDay.message')}</p>
                      <Link
                        to="/kontakt"
                        className="inline-flex items-center gap-1 mt-2 text-sm font-medium text-indigo-400 hover:text-indigo-300 transition-colors"
                      >
                        {t('contactDay.cta')}
                      </Link>
                    </div>
                  </div>
                );
              }

              /* Event WITH time slots */
              if (eventSlots && eventSlots.length > 0) {
                // Full for the viewer also when the remaining seats are held by other people's
                // invitations (consistent with EventSignupModal — then the waitlist is available, not booking).
                const reservedForOthers = Math.max(0, (event.reservedSeats ?? 0) - (event.isReservedForUser ? 1 : 0));
                const isFull = event.currentParticipants + reservedForOthers >= event.maxParticipants;
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <div className="mb-3">
                      <div className="flex items-start justify-between gap-2">
                        <button
                          onClick={() => onEventClick?.(event)}
                          className={clsx("text-base font-semibold text-left hover:underline", color.text)}
                        >
                          {event.title}
                        </button>
                        {event.courseId && (
                          <Link
                            to={`/kursy/${event.courseId}`}
                            state={{ returnTo: courseReturnTo }}
                            className="flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors shrink-0 mt-0.5"
                          >
                            <ExternalLink className="w-3 h-3" />
                            {t('event.courseDetails')}
                          </Link>
                        )}
                      </div>

                      <p className="text-sm text-surface-400 mt-1">
                        <span className={badgeClass}>{label}</span>
                        {event.isMultiDay && (
                          <span>
                            {" "}
                            ·{" "}
                            {format(new Date(event.startDate), "d", {
                              locale,
                            })}{" "}
                            -{" "}
                            {format(new Date(event.endDate), "d MMMM", {
                              locale,
                            })}
                          </span>
                        )}
                      </p>

                      {isFull && event.enrollmentOpen && !event.isUserRegistered && (
                        <button
                          onClick={() => onEventClick?.(event)}
                          className="mt-2 text-sm font-medium text-amber-400 hover:text-amber-300 transition-colors"
                        >
                          {t('day.waitlistHint')}
                        </button>
                      )}
                      {event.isUserRegistered && (
                        <div className="mt-2 flex items-center gap-3">
                          <span className="text-sm font-medium text-primary-400">
                            {t('signedUp')}
                          </span>
                          <button
                            onClick={() => onCancelEvent?.(event.id)}
                            className="flex items-center gap-1 text-xs text-rose-400 hover:text-rose-300 transition-colors"
                          >
                            <X className="w-3.5 h-3.5" />
                            {t('event.cancel')}
                          </button>
                        </div>
                      )}
                    </div>

                    <div className="space-y-3">
                      {eventSlots.map((slot) => (
                        <SlotButton
                          key={slot.id}
                          slot={slot}
                          onSlotClick={onSlotClick}
                        />
                      ))}
                    </div>
                  </div>
                );
              }

              /* Event WITHOUT slots on this day → show availability from EventSummary */
              {
                // Full for the viewer also when the remaining seats are held by other people's
                // invitations (consistent with EventSignupModal — then the waitlist is available, not booking).
                const reservedForOthers = Math.max(0, (event.reservedSeats ?? 0) - (event.isReservedForUser ? 1 : 0));
                const isFull = event.currentParticipants + reservedForOthers >= event.maxParticipants;
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <button
                          onClick={() => onEventClick?.(event)}
                          className={clsx("text-base font-semibold text-left hover:underline", color.text)}
                        >
                          {event.title}
                        </button>

                        <div className="flex items-center gap-4 mt-1 text-sm text-surface-400">
                          <span className={`flex items-center gap-1 ${badgeClass}`}>
                            <Users className="w-4 h-4" />
                            {label}
                          </span>

                          {event.isMultiDay && (
                            <span className="flex items-center gap-1">
                              <Calendar className="w-4 h-4" />
                              {format(new Date(event.startDate), "d", { locale })}{" "}
                              -{" "}
                              {format(new Date(event.endDate), "d MMMM", { locale })}
                            </span>
                          )}
                        </div>

                        {isFull && event.enrollmentOpen && !event.isUserRegistered && (
                          <button
                            onClick={() => onEventClick?.(event)}
                            className="mt-2 text-sm font-medium text-amber-400 hover:text-amber-300 transition-colors"
                          >
                            {t('day.waitlistHint')}
                          </button>
                        )}
                        {!isFull && event.enrollmentOpen && !event.isUserRegistered && (
                          <button
                            onClick={() => onEventClick?.(event)}
                            className="mt-2 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors"
                          >
                            {t('signUp')}
                          </button>
                        )}
                        {event.isUserRegistered && (
                          <div className="mt-2 flex items-center gap-3">
                            <span className="text-sm font-medium text-primary-400">
                              {t('signedUp')}
                            </span>
                            <button
                              onClick={() => onCancelEvent?.(event.id)}
                              className="flex items-center gap-1 text-xs text-rose-400 hover:text-rose-300 transition-colors"
                            >
                              <X className="w-3.5 h-3.5" />
                              {t('event.cancel')}
                            </button>
                          </div>
                        )}
                      </div>
                      {event.courseId && (
                        <Link
                          to={`/kursy/${event.courseId}`}
                          state={{ returnTo: courseReturnTo }}
                          className="flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors shrink-0 mt-0.5"
                        >
                          <ExternalLink className="w-3 h-3" />
                          {t('event.courseDetails')}
                        </Link>
                      )}
                    </div>
                  </div>
                );
              }
            })}

            {/* Availability windows */}
            {standaloneSlots.some(s => s.isAvailabilityWindow) && (
              <div>
                <h3 className="text-base font-semibold text-teal-400 mb-3">
                  {t('day.availabilityWindows')}
                </h3>
                <div className="space-y-3">
                  {standaloneSlots.filter(s => s.isAvailabilityWindow).map((slot) => (
                    <SlotButton
                      key={slot.id}
                      slot={slot}
                      onSlotClick={onSlotClick}
                      showTitle={true}
                    />
                  ))}
                </div>
              </div>
            )}

            {/* Standalone bookable slots */}
            {standaloneSlots.some(s => !s.isAvailabilityWindow) && (
              <div>
                <h3 className="text-base font-semibold text-primary-400 mb-3">
                  {t('day.training')}
                </h3>

                <div className="space-y-3">
                  {standaloneSlots.filter(s => !s.isAvailabilityWindow).map((slot) => (
                    <SlotButton
                      key={slot.id}
                      slot={slot}
                      onSlotClick={onSlotClick}
                      showTitle={true}
                    />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
