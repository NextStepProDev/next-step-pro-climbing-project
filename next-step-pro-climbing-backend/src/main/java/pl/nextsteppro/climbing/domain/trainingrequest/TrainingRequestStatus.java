package pl.nextsteppro.climbing.domain.trainingrequest;

public enum TrainingRequestStatus {
    /** Czeka na reakcję admina. */
    PENDING,
    /** Admin utworzył slot/wydarzenie z tej propozycji (proponujący zaproszony). */
    ACCEPTED,
    /** Admin oznaczył: skontaktował się w celu ustalenia innego terminu. */
    CONTACTED,
    /** Admin odrzucił propozycję. */
    REJECTED,
    /** Proponowana data minęła bez reakcji (scheduler). */
    EXPIRED
}
