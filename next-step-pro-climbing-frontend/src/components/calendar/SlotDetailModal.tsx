import { useState } from "react";
import { useTranslation } from "react-i18next";
import { format } from "date-fns";
import { Clock, Users, Calendar } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Modal } from "../ui/Modal";
import { Button } from "../ui/Button";
import { useAuth } from "../../context/AuthContext";
import { saveRedirectPath } from "../../utils/redirect";
import { reservationApi } from "../../api/client";
import { getErrorMessage } from "../../utils/errors";
import { useDateLocale } from "../../utils/dateFnsLocale";
import type { TimeSlotDetail } from "../../types";

interface SlotDetailModalProps {
  slot: TimeSlotDetail | null;
  isOpen: boolean;
  onClose: () => void;
}

export function SlotDetailModal({
  slot,
  isOpen,
  onClose,
}: SlotDetailModalProps) {
  const { t } = useTranslation('calendar');
  const locale = useDateLocale();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [comment, setComment] = useState("");
  const [participants, setParticipants] = useState(1);

  const reservationMutation = useMutation({
    mutationFn: (data: {
      slotId: string;
      comment?: string;
      participants: number;
    }) => reservationApi.create(data.slotId, data.comment, data.participants),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["reservations"] });
      setComment("");
      onClose();
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (reservationId: string) => reservationApi.cancel(reservationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["reservations"] });
      onClose();
    },
  });

  if (!slot) return null;

  const dateObj = new Date(slot.date);
  const spotsLeft =
    (slot.maxParticipants ?? 0) - (slot.currentParticipants ?? 0);

  const isPast = slot.status === "PAST";
  const isBookingClosed = slot.status === "BOOKING_CLOSED";
  const isAvailable = slot.status === "AVAILABLE" && spotsLeft > 0;

  const handleLoginRedirect = () => {
    saveRedirectPath(`/calendar?date=${slot.date}`);
    navigate("/login");
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('slot.title')}>
      <div className="space-y-6">
        {/* Date and time */}
        <div className="flex items-center gap-4 text-dark-300">
          <div className="flex items-center gap-2">
            <Calendar className="w-5 h-5" />
            <span className="capitalize">
              {format(dateObj, "EEEE, d MMMM", { locale })}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <Clock className="w-5 h-5" />
            <span>
              {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
            </span>
          </div>
        </div>

        {/* Event info */}
        {slot.eventTitle && (
          <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
            <span className="text-sm font-medium text-primary-400">{slot.eventTitle}</span>
            {slot.eventDescription && (
              <p className="text-sm text-dark-300 mt-2 whitespace-pre-wrap">{slot.eventDescription}</p>
            )}
          </div>
        )}

        {/* Capacity */}
        <div className="flex items-center gap-2 text-dark-300">
          <Users className="w-5 h-5" />
          <span>
            {t('slot.participants', { current: slot.currentParticipants, max: slot.maxParticipants })}
            {spotsLeft > 0 && (
              <span className="text-primary-400 ml-2">{t('slot.spotsFree', { count: spotsLeft })}</span>
            )}
          </span>
        </div>

        {/* Past slot info */}
        {isPast && (
          <div className="p-3 bg-dark-800 border border-dark-700 rounded-lg">
            <span className="text-dark-400 text-sm">
              {t('slot.past')}
            </span>
          </div>
        )}

        {/* Booking closed info */}
        {isBookingClosed && !slot.isUserRegistered && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <span className="text-amber-400 text-sm">
              {t('slot.bookingClosed')}
            </span>
          </div>
        )}

        {/* User status */}
        {slot.isUserRegistered && (
          <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
            <span className="text-primary-400 font-medium">
              {t('slot.hasReservation')}
            </span>
          </div>
        )}

        {/* Participants & Comment for reservation */}
        {isAuthenticated && isAvailable && !slot.isUserRegistered && (
          <>
            {spotsLeft > 1 && (
              <div>
                <label className="block text-sm text-dark-400 mb-1">
                  {t('slot.spotsLabel')}
                </label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() =>
                      setParticipants(Math.max(1, participants - 1))
                    }
                    className="w-9 h-9 rounded-lg bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-lg font-bold"
                  >
                    -
                  </button>
                  <span className="text-lg font-semibold text-dark-100 w-8 text-center">
                    {participants}
                  </span>
                  <button
                    type="button"
                    onClick={() =>
                      setParticipants(Math.min(spotsLeft, participants + 1))
                    }
                    className="w-9 h-9 rounded-lg bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-lg font-bold"
                  >
                    +
                  </button>
                  <span className="text-sm text-dark-500">
                    {t('slot.spotsOf', { count: spotsLeft })}
                  </span>
                </div>
              </div>
            )}
            <div>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value.slice(0, 500))}
                placeholder={t('slot.commentPlaceholder')}
                maxLength={500}
                rows={2}
                className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-dark-500"
              />
              <div className="text-xs text-dark-500 text-right mt-1">
                {comment.length}/500
              </div>
            </div>
          </>
        )}

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t border-dark-800">
          {!isAuthenticated ? (
            <Button
              variant="primary"
              className="flex-1"
              onClick={handleLoginRedirect}
            >
              {t('slot.loginToBook')}
            </Button>
          ) : slot.isUserRegistered && slot.reservationId ? (
            <Button
              variant="danger"
              className="flex-1"
              loading={cancelMutation.isPending}
              onClick={() =>
                cancelMutation.mutate(slot.reservationId as string)
              }
            >
              {t('slot.cancelReservation')}
            </Button>

          ) : isAvailable ? (
            <Button
              variant="primary"
              className="flex-1"
              loading={reservationMutation.isPending}
              onClick={() =>
                reservationMutation.mutate({
                  slotId: slot.id,
                  comment: comment || undefined,
                  participants,
                })
              }
            >
              {participants > 1 ? t('slot.bookMultiple', { count: participants }) : t('slot.bookSingle')}
            </Button>
          ) : (
            <Button
              variant="secondary"
              className="flex-1 cursor-not-allowed opacity-50"
              disabled
            >
              {t('slot.noSpots')}
            </Button>
          )}

          <Button variant="ghost" onClick={onClose}>
            {t('slot.close')}
          </Button>
        </div>

        {/* Error messages */}
        {reservationMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(reservationMutation.error)}
          </p>
        )}
        {cancelMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(cancelMutation.error)}
          </p>
        )}
      </div>
    </Modal>
  );
}
