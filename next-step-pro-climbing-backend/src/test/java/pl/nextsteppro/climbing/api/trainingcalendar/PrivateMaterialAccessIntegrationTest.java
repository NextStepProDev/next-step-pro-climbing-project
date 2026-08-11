package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.storage.TestImages;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coach materials after V80: no longer reachable at a public address, streamed with an ownership
 * check instead.
 *
 * <p>Before this, {@code /api/files/training/{uuid}.pdf} was {@code permitAll} and the unguessable
 * filename was the whole protection — so a URL that ever escaped (browser history, a pasted
 * message, a bookmark) worked for anyone, indefinitely.
 */
class PrivateMaterialAccessIntegrationTest extends BaseIntegrationTest {

    @Autowired private TrainingCalendarService trainingCalendarService;
    @Autowired private AdminTrainingCalendarService adminTrainingCalendarService;
    @Autowired private TrainingTemplateService trainingTemplateService;
    @Autowired private PersonalTrainingRepository personalTrainingRepository;
    @Autowired private TrainingCommentRepository trainingCommentRepository;
    @Autowired private TrainingAttachmentRepository attachmentRepository;

    private User athlete;
    private User otherAthlete;
    private User coach;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        trainingCommentRepository.deleteAll();
        personalTrainingRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        athlete = saveAthlete("athlete@example.com", "Anna");
        otherAthlete = saveAthlete("other@example.com", "Ola");

        coach = new User("coach@example.com", "Trener", "Główny", "+48111111111", "coach");
        coach.setRole(UserRole.ADMIN);
        coach.setEmailVerified(true);
        coach = userRepository.save(coach);
    }

    private User saveAthlete(String email, String firstName) {
        User user = new User(email, firstName, "Wspinaczka", "+48123456789", "hash");
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        user.setAthlete(true);
        user.grantTrainingConsent();
        return userRepository.save(user);
    }

    private String uploadPdf() {
        return trainingCalendarService.uploadMyAttachment(athlete.getId(),
            new MockMultipartFile("file", "plan.pdf", "application/pdf", TestImages.pdf())).filename();
    }

    private UUID trainingWithMaterial(User owner) {
        String filename = uploadPdf();
        UUID trainingId = trainingCalendarService.createMy(owner.getId(), new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(1), null, null, "Sesja", null,
            List.of(new AttachmentRequest(
                pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind.FILE,
                null, filename, "plan.pdf", "application/pdf", 100L, "Plan")))).id();
        return trainingId;
    }

    private UUID materialIdOf(UUID trainingId) {
        return attachmentRepository.findByTrainingIdOrderByPositionAsc(trainingId).getFirst().getId();
    }

    @Test
    void shouldStreamAMaterialToItsOwnAthlete() {
        UUID materialId = materialIdOf(trainingWithMaterial(athlete));

        CommentFileStream stream = trainingCalendarService.openMaterial(athlete.getId(), false, materialId);
        assertEquals("application/pdf", stream.mimeType());
        assertTrue(stream.size() > 0);
    }

    @Test
    void shouldStreamAMaterialToTheCoach() {
        UUID materialId = materialIdOf(trainingWithMaterial(athlete));

        assertDoesNotThrow(() -> trainingCalendarService.openMaterial(coach.getId(), true, materialId));
    }

    @Test
    void shouldRefuseAMaterialFromSomebodyElsesTraining() {
        UUID materialId = materialIdOf(trainingWithMaterial(athlete));

        assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.openMaterial(otherAthlete.getId(), false, materialId));
    }

    @Test
    void shouldRefuseATemplateFileToAnyAthlete() {
        String filename = uploadPdf();
        var template = trainingTemplateService.create(new SaveTemplateRequest(
            null, "Szablon", null, 60, null,
            List.of(new AttachmentRequest(
                pl.nextsteppro.climbing.domain.personaltraining.AttachmentKind.FILE,
                null, filename, "plan.pdf", "application/pdf", 100L, "Plan"))));
        UUID materialId = attachmentRepository.findByTemplateIdOrderByPositionAsc(template.id())
            .getFirst().getId();

        // The coach's library is not the athlete's to browse, and that must not come down to
        // whether they can guess an id.
        assertThrows(IllegalArgumentException.class,
            () -> trainingCalendarService.openMaterial(athlete.getId(), false, materialId));
        assertDoesNotThrow(() -> trainingCalendarService.openMaterial(coach.getId(), true, materialId));
    }

    @Test
    void shouldAddressMaterialsByRowRatherThanByFilename() {
        UUID trainingId = trainingWithMaterial(athlete);
        var training = adminTrainingCalendarService.getRangeForAthlete(
            coach.getId(), athlete.getId(), LocalDate.now(), LocalDate.now().plusDays(3)).trainings();

        String url = training.getFirst().attachments().getFirst().url();
        assertNotNull(url);
        assertTrue(url.startsWith("/api/training-calendar/files/"),
            "Materials must be addressed by attachment id, not by the stored filename");
        assertFalse(url.contains(".pdf"));
        assertTrue(url.endsWith(materialIdOf(trainingId).toString()));
    }
}
