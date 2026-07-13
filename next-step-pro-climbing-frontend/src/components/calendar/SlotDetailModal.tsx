import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { format } from "date-fns";
import { Clock, Users, Calendar, Clock3, Phone, Trash2, AlertTriangle, Pencil } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Modal } from "../ui/Modal";
import { Button } from "../ui/Button";
import { SuccessCheckmark } from "../ui/SuccessCheckmark";
import { ShareButtons } from "../ui/ShareButtons";
import { AddToCalendarButton } from "../ui/AddToCalendarButton";
import { CompleteProfileModal } from "../ui/CompleteProfileModal";
import { TimeScrollPicker } from "../ui/TimeScrollPicker";
import { useAuth } from "../../context/AuthContext";
import { saveRedirectPath } from "../../utils/redirect";
import { adminApi, reservationApi } from "../../api/client";
import { getErrorMessage } from "../../utils/errors";
import { useDateLocale } from "../../utils/dateFnsLocale";
import type { TimeSlotDetail } from "../../types";

interface SlotDetailModalProps {
  slot: TimeSlotDetail | null;
  isOpen: boolean;
  onClose: () => void;
  /** Availability window: opens the training request form constrained to the window bounds. */
  onProposeInWindow?: (w: { slotId: string; date: string; startTime: string; endTime: string }) => void;
}

export function SlotDetailModal({
  slot,
  isOpen,
  onClose,
  onProposeInWindow,
}: SlotDetailModalProps) {
  const { t } = useTranslation('calendar');
  const { t: ta } = useTranslation('admin');
  const locale = useDateLocale();
  const { isAuthenticated, isAdmin, user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [comment, setComment] = useState("");
  const [participants, setParticipants] = useState(1);
  const [showParticipants, setShowParticipants] = useState(false);
  const [showCompleteProfile, setShowCompleteProfile] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [editForm, setEditForm] = useState({
    title: '',
    startTime: '',
    endTime: '',
    maxParticipants: 1,
    isAvailabilityWindow: false,
  });
  const pendingAction = useRef<(() => void) | null>(null);

  const requireProfile = (action: () => void) => {
    if (user?.firstName && user?.lastName && user?.phone) {
      action();
    } else {
      pendingAction.current = action;
      setShowCompleteProfile(true);
    }
  };


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
      setShowSuccess(true);
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

  const joinWaitlistMutation = useMutation({
    mutationFn: (slotId: string) => reservationApi.joinWaitlist(slotId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["reservations", "waitlist"] });
      onClose();
    },
  });

  const leaveWaitlistMutation = useMutation({
    mutationFn: (slotId: string) => reservationApi.leaveWaitlist(slotId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["reservations", "waitlist"] });
      onClose();
    },
  });

  const confirmOfferMutation = useMutation({
    mutationFn: (waitlistId: string) => reservationApi.confirmWaitlistOffer(waitlistId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["reservations"] });
      onClose();
    },
  });

  const { data: deleteConfirmParticipants } = useQuery({
    queryKey: ['admin', 'participants', slot?.id],
    queryFn: () => adminApi.getSlotParticipants(slot!.id),
    enabled: isAdmin && showDeleteConfirm && !!slot,
  });

  const deleteSlotMutation = useMutation({
    mutationFn: adminApi.deleteTimeSlot,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["reservations"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "slots"] });
      setShowDeleteConfirm(false);
      onClose();
    },
  });

  const editSlotMutation = useMutation({
    mutationFn: (data: { startTime: string; endTime: string; maxParticipants: number; title: string; isAvailabilityWindow: boolean }) =>
      adminApi.updateTimeSlot(slot!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["calendar"] });
      queryClient.invalidateQueries({ queryKey: ["slot"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "slots"] });
      setEditMode(false);
    },
  });

  if (!slot) return null;

  const isAvailabilityWindow = slot.isAvailabilityWindow;
  const dateObj = new Date(slot.date);
  // Invitation-held seats: unavailable to non-invitees. The viewer's own invitation does not
  // block — that is why we subtract only OTHER people's invitations.
  const reservedSeats = slot.reservedSeats ?? 0;
  const reservedForOthers = Math.max(0, reservedSeats - (slot.isReservedForUser ? 1 : 0));
  const spotsLeft =
    (slot.maxParticipants ?? 0) - (slot.currentParticipants ?? 0) - reservedForOthers;

  const isPast = slot.status === "PAST";
  // PAST status arrives from the START time — for the admin a slot is "finished" only after
  // its end time (an ongoing slot can still be edited/deleted, consistent with drag in WeekCalendar).
  const hasEnded = isPast && new Date(`${slot.date}T${slot.endTime}`) < new Date();
  const isBookingClosed = slot.status === "BOOKING_CLOSED";
  const isAvailable = slot.status === "AVAILABLE" && spotsLeft > 0;
  const isFull = slot.status === "FULL" || (slot.status === "AVAILABLE" && spotsLeft <= 0);
  // A non-invited viewer for whom only held seats remain
  const blockedByReserved = isFull && reservedForOthers > 0 && !slot.isReservedForUser && !slot.isUserRegistered;
  // The waitlist makes sense when there is no seat for this viewer — also when only
  // invitation-held seats block. They free up when a confirmed person cancels OR the admin
  // removes an invitation (both call notifyAll), so the queue is not a dead end. The backend
  // counts other people's held seats as taken when joining the queue, so frontend and API agree.
  const isWaiting = slot.userWaitlistStatus === "WAITING";
  const isPendingConfirmation = slot.userWaitlistStatus === "PENDING_CONFIRMATION";
  const canJoinWaitlist = isFull && !isWaiting && !isPendingConfirmation && !isPast && !isBookingClosed && !slot.isUserRegistered;

  // Admin inline edit: "Save changes" stays disabled until the form actually
  // differs from the slot's current values (baseline set when entering edit mode).
  const editBaseline = {
    title: slot.title ?? '',
    startTime: slot.startTime.slice(0, 5),
    endTime: slot.endTime.slice(0, 5),
    maxParticipants: slot.maxParticipants,
    isAvailabilityWindow: slot.isAvailabilityWindow,
  };
  const editTimeError = editForm.endTime <= editForm.startTime;
  const editDirty = JSON.stringify(editForm) !== JSON.stringify(editBaseline);

  const handleLoginRedirect = () => {
    saveRedirectPath(`/calendar?date=${slot.date}`);
    navigate("/login");
  };

  return (
    <>
    {showSuccess && <SuccessCheckmark onDone={() => { setShowSuccess(false); onClose(); }} />}
    <Modal isOpen={isOpen} onClose={onClose} title={t('slot.title')}>
      <div className="space-y-6">
        {/* Date and time */}
        <div className="flex items-center gap-4 text-surface-300">
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
              <p className="text-sm text-surface-300 mt-2 whitespace-pre-wrap">{slot.eventDescription}</p>
            )}
          </div>
        )}

        {/* Availability window — proposal form entry (+ phone as fallback) */}
        {isAvailabilityWindow && (
          <>
            <div className="p-4 bg-teal-500/10 border border-teal-500/20 rounded-lg space-y-2">
              <div className="flex items-center gap-2">
                <Phone className="w-5 h-5 text-teal-400 shrink-0" />
                <span className="text-teal-300 font-semibold">{t('slot.availabilityWindow.title')}</span>
              </div>
              <p className="text-teal-200/80 text-sm">{t('slot.availabilityWindow.body')}</p>
            </div>
            {onProposeInWindow && !isAdmin && !isPast && (
              <Button
                variant="primary"
                className="w-full"
                onClick={() => onProposeInWindow({
                  slotId: slot.id,
                  date: slot.date,
                  startTime: slot.startTime,
                  endTime: slot.endTime,
                })}
              >
                {t('slot.availabilityWindow.propose')}
              </Button>
            )}
            <ShareButtons
              title={slot.eventTitle || t('slot.title')}
              url={`${window.location.origin}/calendar?date=${slot.date}&slot=${slot.id}`}
              description={`${format(dateObj, "EEEE, d MMMM", { locale })} ${slot.startTime.slice(0, 5)} - ${slot.endTime.slice(0, 5)}`}
            />
            <div className="flex gap-3 pt-4 border-t border-surface-800">
              <Button variant="ghost" className="flex-1" onClick={onClose}>
                {t('slot.close')}
              </Button>
            </div>
          </>
        )}

        {/* Capacity */}
        {!isAvailabilityWindow && <div className="flex items-center gap-2 text-surface-300">
          <Users className="w-5 h-5" />
          <span>
            {t('slot.participants', { current: slot.currentParticipants, max: slot.maxParticipants })}
            {spotsLeft > 0 && (
              <span className="text-green-300 ml-2">{t('slot.spotsFree', { count: spotsLeft })}</span>
            )}
            {reservedForOthers > 0 && (!isAuthenticated || slot.isReservedForUser) && (
              <span className="text-violet-300/90 ml-2">{t('slot.reservedForInvited', { count: reservedForOthers })}</span>
            )}
          </span>
        </div>}

        {/* Invitee: seat held for you */}
        {!isAvailabilityWindow && slot.isReservedForUser && !slot.isUserRegistered && !isPast && (
          <div className="p-3 bg-violet-500/10 border border-violet-500/30 rounded-lg">
            <span className="text-violet-200 font-medium">🎟 {t('slot.reservedForYou')}</span>
          </div>
        )}

        {/* Explains a seemingly free counter (e.g. "0 / 1") when the remaining seats are invitation-held.
            Anonymous users get a login hint; a logged-in non-invitee gets the brief fact + the waitlist. */}
        {!isAvailabilityWindow && blockedByReserved && !isPast && (
          <div className="p-3 bg-violet-500/10 border border-violet-500/20 rounded-lg">
            <span className="text-violet-200/90 text-sm">
              {isAuthenticated ? t('slot.reservedOnlyLeft') : t('slot.reservedOnlyLeftGuest')}
            </span>
          </div>
        )}

        {/* Past slot info — the admin is not shown "ended" for an ongoing slot (editing still active) */}
        {!isAvailabilityWindow && isPast && (!isAdmin || hasEnded) && (
          <div className="p-3 bg-surface-800 border border-surface-700 rounded-lg">
            <span className="text-surface-400 text-sm">
              {t('slot.past')}
            </span>
          </div>
        )}

        {/* Booking closed info */}
        {!isAvailabilityWindow && isBookingClosed && !slot.isUserRegistered && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <span className="text-amber-400 text-sm">
              {t('slot.bookingClosed')}
            </span>
          </div>
        )}

        {/* User status */}
        {!isAvailabilityWindow && slot.isUserRegistered && (
          <div className="space-y-3">
            <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
              <span className="text-primary-400 font-medium">
                {t('slot.hasReservation')}
              </span>
            </div>
            <AddToCalendarButton
              title={slot.eventTitle || t('slot.title')}
              date={slot.date}
              startTime={slot.startTime}
              endTime={slot.endTime}
            />
          </div>
        )}

        {/* Waitlist — PENDING_CONFIRMATION (race for the seat) */}
        {!isAvailabilityWindow && isPendingConfirmation && slot.waitlistEntryId && (
          <div className="p-4 bg-amber-500/10 border-2 border-amber-500/40 rounded-lg space-y-2">
            <div className="flex items-center gap-2">
              <Clock3 className="w-5 h-5 text-amber-400 shrink-0" />
              <span className="text-amber-300 font-semibold">{t('slot.waitlist.offerTitle')}</span>
            </div>
            <p className="text-amber-200/80 text-sm">{t('slot.waitlist.offerBody')}</p>
            {slot.confirmationDeadline && (
              <p className="text-amber-200/70 text-sm">
                {t('slot.waitlist.deadline')}{' '}
                <span className="font-medium">
                  {format(new Date(slot.confirmationDeadline), 'dd.MM.yyyy HH:mm')}
                </span>
              </p>
            )}
          </div>
        )}

        {/* Waitlist — race lost (someone was faster — you are back in the queue) */}
        {!isAvailabilityWindow && confirmOfferMutation.isError && (
          <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
            <p className="text-rose-400 text-sm font-medium">
              {getErrorMessage(confirmOfferMutation.error)}
            </p>
          </div>
        )}

        {/* Waitlist — WAITING (in the queue) */}
        {!isAvailabilityWindow && isWaiting && (
          <div className="p-3 bg-surface-800 border border-surface-700 rounded-lg">
            <span className="text-surface-300 text-sm">
              {t('slot.waitlist.waiting')}
            </span>
          </div>
        )}

        {/* Participants & Comment for reservation */}
        {!isAvailabilityWindow && isAuthenticated && isAvailable && !slot.isUserRegistered && (
          <>
            {spotsLeft > 1 && !showParticipants && (
              <button
                type="button"
                onClick={() => setShowParticipants(true)}
                className="text-sm text-primary-400 hover:text-primary-300 transition-colors text-left"
              >
                {t('slot.addMoreParticipants')} →
              </button>
            )}
            {spotsLeft > 1 && showParticipants && (
              <div>
                <label className="block text-sm text-surface-400 mb-1">
                  {t('slot.spotsLabel')}
                </label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() =>
                      setParticipants(Math.max(1, participants - 1))
                    }
                    className="w-9 h-9 rounded-lg bg-surface-800 border border-surface-700 text-surface-200 hover:bg-surface-700 transition-colors text-lg font-bold"
                  >
                    -
                  </button>
                  <span className="text-lg font-semibold text-surface-100 w-8 text-center">
                    {participants}
                  </span>
                  <button
                    type="button"
                    onClick={() =>
                      setParticipants(Math.min(spotsLeft, participants + 1))
                    }
                    className="w-9 h-9 rounded-lg bg-surface-800 border border-surface-700 text-surface-200 hover:bg-surface-700 transition-colors text-lg font-bold"
                  >
                    +
                  </button>
                  <span className="text-sm text-surface-500">
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
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-surface-500"
              />
              <div className="text-xs text-surface-500 text-right mt-1">
                {comment.length}/500
              </div>
            </div>
          </>
        )}

        {/* Share */}
        {!isAvailabilityWindow && (
          <ShareButtons
            title={slot.eventTitle || t('slot.title')}
            url={`${window.location.origin}/calendar?date=${slot.date}&slot=${slot.id}`}
            description={`${format(dateObj, "EEEE, d MMMM", { locale })} ${slot.startTime.slice(0, 5)} - ${slot.endTime.slice(0, 5)}`}
          />
        )}

        {/* Admin edit & delete */}
        {isAdmin && !hasEnded && (
          <>
            {editMode ? (
              <div className="p-4 bg-surface-800/50 border border-surface-700 rounded-lg space-y-4">
                <h3 className="text-sm font-semibold text-surface-200">{ta('slots.editTitle')}</h3>
                <div>
                  <label className="block text-sm text-surface-400 mb-1">{ta('slots.titleLabel')}</label>
                  <input
                    type="text"
                    value={editForm.title}
                    onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
                    placeholder={ta('slots.titlePlaceholder')}
                    maxLength={200}
                    className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 text-sm"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <TimeScrollPicker
                    label={ta('slots.from')}
                    value={editForm.startTime}
                    onChange={(v) => setEditForm({ ...editForm, startTime: v })}
                  />
                  <TimeScrollPicker
                    label={ta('slots.to')}
                    value={editForm.endTime}
                    onChange={(v) => setEditForm({ ...editForm, endTime: v })}
                  />
                </div>
                {editForm.endTime <= editForm.startTime && (
                  <p className="text-sm text-rose-400/80">{ta('slots.endAfterStart')}</p>
                )}
                <label className="flex items-start gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={editForm.isAvailabilityWindow}
                    onChange={(e) => setEditForm({ ...editForm, isAvailabilityWindow: e.target.checked })}
                    className="mt-0.5 accent-teal-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-teal-300">{ta('slots.availabilityWindow')}</span>
                    <p className="text-xs text-surface-400 mt-0.5">{ta('slots.availabilityWindowHint')}</p>
                  </div>
                </label>
                {!editForm.isAvailabilityWindow && (
                  <div>
                    <label className="block text-sm text-surface-400 mb-1">{ta('slots.maxParticipants')}</label>
                    <input
                      type="number"
                      min={0}
                      value={editForm.maxParticipants}
                      onChange={(e) => setEditForm({ ...editForm, maxParticipants: parseInt(e.target.value) })}
                      className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 text-sm"
                    />
                  </div>
                )}
                <div className="flex gap-3">
                  <Button
                    loading={editSlotMutation.isPending}
                    disabled={!editDirty || editTimeError}
                    className="flex-1"
                    onClick={() => {
                      if (editForm.endTime <= editForm.startTime) return;
                      editSlotMutation.mutate({
                        startTime: editForm.startTime,
                        endTime: editForm.endTime,
                        maxParticipants: editForm.isAvailabilityWindow ? 1 : editForm.maxParticipants,
                        title: editForm.title || '',
                        isAvailabilityWindow: editForm.isAvailabilityWindow,
                      });
                    }}
                  >
                    {ta('slots.saveChanges')}
                  </Button>
                  <Button variant="ghost" onClick={() => setEditMode(false)}>
                    {ta('slots.cancel')}
                  </Button>
                </div>
                {editSlotMutation.isError && (
                  <p className="text-sm text-rose-400/80">{getErrorMessage(editSlotMutation.error)}</p>
                )}
              </div>
            ) : (
              <button
                type="button"
                onClick={() => {
                  setEditForm({
                    title: slot.title ?? '',
                    startTime: slot.startTime.slice(0, 5),
                    endTime: slot.endTime.slice(0, 5),
                    maxParticipants: slot.maxParticipants,
                    isAvailabilityWindow: slot.isAvailabilityWindow,
                  });
                  setEditMode(true);
                }}
                className="flex items-center gap-2 text-sm text-primary-400 hover:text-primary-300 transition-colors"
              >
                <Pencil className="w-4 h-4" />
                {ta('slots.editSlot')}
              </button>
            )}
          </>
        )}
        {isAdmin && (
          <button
            type="button"
            onClick={() => setShowDeleteConfirm(true)}
            className="flex items-center gap-2 text-sm text-rose-400/70 hover:text-rose-400 transition-colors"
          >
            <Trash2 className="w-4 h-4" />
            {ta('slots.deleteSlot')}
          </button>
        )}

        {/* Actions */}
        {!isAvailabilityWindow && <div className="flex gap-3 pt-4 border-t border-surface-800">
          {!isAuthenticated ? (
            <Button
              variant="primary"
              className="flex-1"
              onClick={handleLoginRedirect}
            >
              {t('slot.loginToBook')}
            </Button>
          ) : isPendingConfirmation && slot.waitlistEntryId ? (
            <>
              <Button
                variant="primary"
                className="flex-1"
                loading={confirmOfferMutation.isPending}
                onClick={() => requireProfile(() => confirmOfferMutation.mutate(slot.waitlistEntryId as string))}
              >
                {t('slot.waitlist.confirm')}
              </Button>
              <Button
                variant="danger"
                loading={leaveWaitlistMutation.isPending}
                onClick={() => leaveWaitlistMutation.mutate(slot.id)}
              >
                {t('slot.waitlist.decline')}
              </Button>
            </>
          ) : isWaiting ? (
            <Button
              variant="secondary"
              className="flex-1"
              loading={leaveWaitlistMutation.isPending}
              onClick={() => leaveWaitlistMutation.mutate(slot.id)}
            >
              {t('slot.waitlist.leave')}
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
                requireProfile(() =>
                  reservationMutation.mutate({
                    slotId: slot.id,
                    comment: comment || undefined,
                    participants,
                  })
                )
              }
            >
              {participants > 1 ? t('slot.bookMultiple', { count: participants }) : t('slot.bookSingle')}
            </Button>
          ) : canJoinWaitlist ? (
            <Button
              variant="secondary"
              className="flex-1"
              loading={joinWaitlistMutation.isPending}
              onClick={() => requireProfile(() => joinWaitlistMutation.mutate(slot.id))}
            >
              {t('slot.waitlist.join')}
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
        </div>}

        {/* Error messages */}
        {!isAvailabilityWindow && reservationMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(reservationMutation.error)}
          </p>
        )}
        {!isAvailabilityWindow && cancelMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(cancelMutation.error)}
          </p>
        )}
        {!isAvailabilityWindow && joinWaitlistMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(joinWaitlistMutation.error)}
          </p>
        )}
      </div>
    </Modal>

    {showCompleteProfile && (
      <CompleteProfileModal
        onCompleted={() => {
          setShowCompleteProfile(false);
          pendingAction.current?.();
          pendingAction.current = null;
        }}
        onClose={() => {
          setShowCompleteProfile(false);
          pendingAction.current = null;
        }}
      />
    )}

    {showDeleteConfirm && deleteConfirmParticipants && (
      <Modal
        isOpen={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        title={deleteConfirmParticipants.participants.length > 0
          ? ta('slots.warningActiveReservations')
          : ta('slots.deleteTitle')}
      >
        <div className="space-y-4">
          <div className="text-sm text-surface-400">
            {format(dateObj, 'EEEE, d MMMM', { locale })} |{' '}
            {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
          </div>

          {deleteConfirmParticipants.participants.length > 0 ? (
            <>
              <div className="flex items-start gap-3 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
                <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
                <div className="text-sm text-rose-300">
                  <p className="font-medium mb-1">{ta('slots.hasActiveReservations')}</p>
                  <p className="text-rose-400/80">{ta('slots.deleteWarning')}</p>
                </div>
              </div>
              <div>
                <h3 className="text-sm font-medium text-surface-300 mb-2">
                  {ta('slots.registered', { count: deleteConfirmParticipants.participants.length })}
                </h3>
                <ul className="space-y-2 max-h-48 overflow-y-auto">
                  {deleteConfirmParticipants.participants.map((p) => (
                    <li key={p.userId} className="bg-surface-800 rounded-lg p-3">
                      <div className="font-medium text-surface-100">{p.fullName}</div>
                      <div className="text-sm text-surface-400">{p.email}</div>
                    </li>
                  ))}
                </ul>
              </div>
            </>
          ) : (
            <p className="text-surface-400 text-sm">{ta('slots.noRegistered')}</p>
          )}

          <div className="flex gap-3 pt-2">
            <Button
              variant="danger"
              className="flex-1"
              loading={deleteSlotMutation.isPending}
              onClick={() => deleteSlotMutation.mutate(slot.id)}
            >
              {deleteConfirmParticipants.participants.length > 0
                ? ta('slots.deleteAndCancel')
                : ta('slots.deleteSimple')}
            </Button>
            <Button variant="ghost" onClick={() => setShowDeleteConfirm(false)}>
              {ta('slots.cancel')}
            </Button>
          </div>

          {deleteSlotMutation.isError && (
            <p className="text-sm text-rose-400/80">
              {getErrorMessage(deleteSlotMutation.error)}
            </p>
          )}
        </div>
      </Modal>
    )}
    </>
  );
}
