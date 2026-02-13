import { useMemo } from "react";
import { format } from "date-fns";
import { pl } from "date-fns/locale";
import { ArrowLeft, Clock, Calendar, Users, Plus } from "lucide-react";
import clsx from "clsx";
import type { TimeSlot, EventSummary } from "../../types";
import { formatAvailability, getEventColor } from "../../utils/events";

interface DayViewProps {
  date: string;
  slots: TimeSlot[];
  events: EventSummary[];
  eventColorMap: Map<string, number>;
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
              Twoja rezerwacja
            </span>
          )}
          {slot.status === "AVAILABLE" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-primary-500/10 text-primary-400 rounded">
              Dostępne
            </span>
          )}
          {slot.status === "FULL" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-amber-500/10 text-amber-400 rounded">
              Pełne
            </span>
          )}
          {slot.status === "BLOCKED" && (
            <span className="px-2 py-1 text-xs font-medium bg-dark-700 text-dark-400 rounded">
              Zarezerwowane
            </span>
          )}
          {slot.status === "BOOKING_CLOSED" && !slot.isUserRegistered && (
            <span className="px-2 py-1 text-xs font-medium bg-amber-500/10 text-amber-400 rounded">
              Zadzwoń
            </span>
          )}
          {slot.status === "PAST" && (
            <span className="px-2 py-1 text-xs font-medium bg-dark-700 text-dark-500 rounded">
              Zakończone
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
  eventColorMap,
  onBack,
  onSlotClick,
  onEventClick,
  onAddSlot,
}: DayViewProps) {
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
          {format(dateObj, "EEEE, d MMMM yyyy", { locale: pl })}
        </h2>

        {onAddSlot && (
          <button
            onClick={onAddSlot}
            title="Dodaj termin"
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
            Brak dostępnych godzin w tym dniu
          </div>
        ) : (
          <>
            {/* Event sections */}
            {events.map((event) => {
              const eventSlots = eventSlotGroups.get(event.title);
              const { label, badgeClass } = formatAvailability(event);
              const color = getEventColor(eventColorMap.get(event.id) ?? 0);

              /* Event WITH time slots */
              if (eventSlots && eventSlots.length > 0) {
                return (
                  <div
                    key={event.id}
                    className={clsx("rounded-lg border p-4", color.border, color.bg)}
                  >
                    <div className="mb-3">
                      <h3 className={clsx("text-base font-semibold", color.text)}>
                        {event.title}
                      </h3>

                      <p className="text-sm text-dark-400 mt-1">
                        <span className={badgeClass}>{label}</span>
                        {event.isMultiDay && (
                          <span>
                            {" "}
                            ·{" "}
                            {format(new Date(event.startDate), "d", {
                              locale: pl,
                            })}{" "}
                            -{" "}
                            {format(new Date(event.endDate), "d MMMM", {
                              locale: pl,
                            })}
                          </span>
                        )}
                      </p>
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
              return (
                <button
                  key={event.id}
                  onClick={() => onEventClick?.(event)}
                  className={clsx(
                    "w-full rounded-lg border p-4 text-left transition-all",
                    color.border, color.bg, color.hoverBorder
                  )}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className={clsx("text-base font-semibold", color.text)}>
                        {event.title}
                      </h3>

                      <div className="flex items-center gap-4 mt-1 text-sm text-dark-400">
                        <span
                          className={`flex items-center gap-1 ${badgeClass}`}
                        >
                          <Users className="w-4 h-4" />
                          {label}
                        </span>

                        {event.isMultiDay && (
                          <span className="flex items-center gap-1">
                            <Calendar className="w-4 h-4" />
                            {format(new Date(event.startDate), "d", {
                              locale: pl,
                            })}{" "}
                            -{" "}
                            {format(new Date(event.endDate), "d MMMM", {
                              locale: pl,
                            })}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </button>
              );
            })}

            {/* Standalone slots */}
            {standaloneSlots.length > 0 && (
              <div>
                <h3 className="text-base font-semibold text-primary-400 mb-3">
                  Trening
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
