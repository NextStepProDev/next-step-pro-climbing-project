import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { format } from "date-fns";
import { ArrowLeft, Clock, Calendar, Users, Plus, ExternalLink } from "lucide-react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import type { TimeSlot, EventSummary } from "../../types";
import { formatAvailability, getEventColorForDisplay } from "../../utils/events";
import { useDateLocale } from "../../utils/dateFnsLocale";

interface DayViewProps {
  date: string;
  slots: TimeSlot[];
  events: EventSummary[];
  onBack: () => void;
  onSlotClick: (slotId: string) => void;
  onEventClick?: (event: EventSummary) => void;
  onAddSlot?: () => void;
}

/* ===============================
   Slot button
   =============================== */
function SlotButton({
  slot,
  onSlotClick,
}: {
  slot: TimeSlot;
  onSlotClick: (slotId: string) => void;
}) {
  const { t } = useTranslation('calendar');

  return (
    <button
      onClick={() => onSlotClick(slot.id)}
      disabled={slot.status === "BLOCKED" || slot.status === "PAST"}
      className={clsx(
        "w-full p-4 rounded-lg border transition-all text-left",
        slot.status === "AVAILABLE" &&
          "border-dark-700 hover:border-primary-500 hover:bg-dark-800",
        slot.status === "FULL" &&
          "border-dark-700 hover:border-amber-500 hover:bg-dark-800",
        slot.status === "BLOCKED" &&
          "border-dark-800 bg-dark-900/50 cursor-not-allowed opacity-50",
        slot.status === "PAST" &&
          "border-dark-800 bg-dark-900/50 cursor-not-allowed opacity-40",
        slot.status === "BOOKING_CLOSED" &&
          "border-dark-700 hover:border-amber-500 hover:bg-dark-800",
        slot.isUserRegistered && "border-primary-500 bg-primary-500/10",
      )}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Clock className="w-5 h-5 text-dark-400" />
          <span className="font-medium text-dark-100">
            {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-primary-500/20 text-primary-400 rounded">
              {t('day.yourReservation')}
            </span>
          )}
          {slot.status === "AVAILABLE" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-primary-500/10 text-primary-400 rounded">
              {t('day.available')}
            </span>
          )}
          {slot.status === "FULL" && !slot.isUserRegistered && (
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
            <span className="px-2 py-1 text-xs font-medium bg-dark-700 text-dark-400 rounded">
              {t('day.blocked')}
            </span>
          )}
          {slot.status === "BOOKING_CLOSED" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-amber-500/10 text-amber-400 rounded">
              {t('day.bookingClosed')}
            </span>
          )}
          {slot.status === "PAST" && (
            <span className="px-2 py-1 text-xs font-medium bg-dark-700 text-dark-500 rounded">
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
  onAddSlot,
}: DayViewProps) {
  const { t } = useTranslation('calendar');
  const locale = useDateLocale();
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
    <div className="bg-dark-900 rounded-xl border border-dark-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-4 p-4 border-b border-dark-800">
        <button
          onClick={onBack}
          className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <h2 className="text-lg font-semibold text-dark-100 capitalize flex-1">
          {format(dateObj, "EEEE, d MMMM yyyy", { locale })}
        </h2>

        {onAddSlot && (
          <button
            onClick={onAddSlot}
            title={t('createSlot.title')}
            className="p-2 text-dark-400 hover:text-primary-400 hover:bg-dark-800 rounded-lg transition-colors"
          >
            <Plus className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Content */}
      <div className="p-4 space-y-6">
        {!hasAnyContent ? (
          <div className="text-center py-8 text-dark-400">
            {t('day.noSlots')}
          </div>
        ) : (
          <>
            {/* Event sections */}
            {events.map((event) => {
              const eventSlots = eventSlotGroups.get(event.title);
              const { label, badgeClass } = formatAvailability(event);
              const color = getEventColorForDisplay(event.eventType, event.currentParticipants >= event.maxParticipants);

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
                      <p className="text-sm text-dark-300 mb-3">{event.description}</p>
                    )}
                    <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/20">
                      <p className="text-sm text-rose-300">{t('contactDay.message')}</p>
                      <Link
                        to="/kontakt"
                        className="inline-flex items-center gap-1 mt-2 text-sm font-medium text-rose-400 hover:text-rose-300 transition-colors"
                      >
                        {t('contactDay.cta')}
                      </Link>
                    </div>
                  </div>
                );
              }

              /* Event WITH time slots */
              if (eventSlots && eventSlots.length > 0) {
                const isFull = event.currentParticipants >= event.maxParticipants;
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <div className="mb-3">
                      <div className="flex items-start justify-between gap-2">
                        <h3 className={clsx("text-base font-semibold", color.text)}>
                          {event.title}
                        </h3>
                        {event.courseId && (
                          <Link
                            to={`/kursy#course-${event.courseId}`}
                            className="flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors shrink-0 mt-0.5"
                          >
                            <ExternalLink className="w-3 h-3" />
                            {t('event.courseDetails')}
                          </Link>
                        )}
                      </div>

                      <p className="text-sm text-dark-400 mt-1">
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
                        <span className="mt-2 inline-block text-sm font-medium text-primary-400">
                          {t('signedUp')}
                        </span>
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
                const isFull = event.currentParticipants >= event.maxParticipants;
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h3 className={clsx("text-base font-semibold", color.text)}>
                          {event.title}
                        </h3>

                        <div className="flex items-center gap-4 mt-1 text-sm text-dark-400">
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
                          <span className="mt-2 inline-block text-sm font-medium text-primary-400">
                            {t('signedUp')}
                          </span>
                        )}
                      </div>
                      {event.courseId && (
                        <Link
                          to={`/kursy#course-${event.courseId}`}
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

            {/* Standalone slots */}
            {standaloneSlots.length > 0 && (
              <div>
                <h3 className="text-base font-semibold text-primary-400 mb-3">
                  {t('day.training')}
                </h3>

                <div className="space-y-3">
                  {standaloneSlots.map((slot) => (
                    <SlotButton
                      key={slot.id}
                      slot={slot}
                      onSlotClick={onSlotClick}
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
