package pl.nextsteppro.climbing.api.admin.instructor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Team members and articles in the CMS — the paths the 2026-10-18 audit read for the first time. */
class InstructorCmsIntegrationTest extends BaseIntegrationTest {

    @Autowired private AdminInstructorService instructors;
    @Autowired private InstructorRepository instructorRepository;

    @BeforeEach
    void setUp() {
        instructorRepository.deleteAll();
    }

    private Instructor member(String name, String language, UUID group, int order) {
        Instructor i = new Instructor(name, "Test");
        i.setLanguage(language);
        i.setTranslationGroupId(group);
        i.setMemberType(InstructorType.INSTRUCTOR);
        i.setDisplayOrder(order);
        return instructorRepository.saveAndFlush(i);
    }

    private static MockMultipartFile png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "ja.png", "image/png", out.toByteArray());
    }

    @Test
    void shouldShowAnUploadedPhotoEvenWhenAGalleryPhotoWasSetBefore() throws Exception {
        Instructor ola = member("Ola", "pl", UUID.randomUUID(), 0);
        ola.setPhotoExternalUrl("https://nextsteppro.pl/api/files/gallery/stare.jpg");
        instructorRepository.saveAndFlush(ola);

        instructors.uploadPhoto(ola.getId(), png());

        // The external URL wins when both exist, so the new upload was stored and never displayed.
        Instructor after = instructorRepository.findById(ola.getId()).orElseThrow();
        assertNull(after.getPhotoExternalUrl());
        assertNotNull(after.getPhotoFilename());
    }

    @Test
    void shouldPlaceANewTranslationWhereTheMemberAlreadyIs() {
        UUID group = UUID.randomUUID();
        member("Pierwsza", "pl", UUID.randomUUID(), 0);
        Instructor second = member("Druga", "pl", group, 5);

        var copy = instructors.duplicateAsTranslation(second.getId(), "en");

        // A fresh min-1 put every new translation at the top of its language's list.
        assertEquals(5, instructorRepository.findById(copy.id()).orElseThrow().getDisplayOrder());
    }

    @Test
    void shouldMoveAMemberEvenWhenTwoShareAPosition() {
        // Positions are copied across languages, so two people in one list can hold the same number;
        // swapping two equal numbers was an arrow that did nothing.
        Instructor a = member("A", "pl", UUID.randomUUID(), 3);
        Instructor b = member("B", "pl", UUID.randomUUID(), 3);
        int before = instructorRepository.findById(b.getId()).orElseThrow().getDisplayOrder();

        instructors.moveUp(b.getId());

        int aAfter = instructorRepository.findById(a.getId()).orElseThrow().getDisplayOrder();
        int bAfter = instructorRepository.findById(b.getId()).orElseThrow().getDisplayOrder();
        assertNotEquals(aAfter, bAfter);
        assertEquals(true, bAfter < aAfter, "B is now above A");
        assertEquals(3, before);
    }

    @Test
    void shouldClearABioWhenTheEditorSendsItEmpty() {
        Instructor ola = member("Ola", "pl", UUID.randomUUID(), 0);
        ola.setBio("Stary opis");
        instructorRepository.saveAndFlush(ola);

        instructors.updateInstructor(ola.getId(), new AdminInstructorDtos.UpdateInstructorRequest(
            null, null, "", null, null, null, null, null, null, null));

        assertNull(instructorRepository.findById(ola.getId()).orElseThrow().getBio());
    }
}
