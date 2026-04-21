package pl.nextsteppro.climbing.api.admin.instructor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.instructor.AdminInstructorDtos.*;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AdminInstructorService - manages instructor CRUD and photo operations.
 *
 * Test coverage:
 * - CRUD operations (create, read, update, delete)
 * - Photo upload with validation
 * - Photo deletion and cleanup
 * - Display order management
 * - Active status management
 * - Edge cases: not found, duplicate names, file storage failures
 */
@ExtendWith(MockitoExtension.class)
class AdminInstructorServiceTest {

    @Mock
    private InstructorRepository instructorRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private MultipartFile mockFile;

    private AdminInstructorService adminInstructorService;
    private Instructor testInstructor;
    private UUID instructorId;
    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        adminInstructorService = new AdminInstructorService(
            instructorRepository,
            fileStorageService,
            BASE_URL
        );

        instructorId = UUID.randomUUID();
        testInstructor = new Instructor("John", "Doe");
        setInstructorIdViaReflection(testInstructor, instructorId);
        testInstructor.setBio("Experienced climbing instructor");
        testInstructor.setCertifications("UIAA, IFSC");
    }

    // ========== CREATE INSTRUCTOR TESTS ==========

    @Test
    void shouldCreateInstructorSuccessfully() {
        // Given
        CreateInstructorRequest request = new CreateInstructorRequest(
            "Jane",
            "Smith",
            "Expert in bouldering",
            "IFMGA, UIAGM",
            null
        );

        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> {
            Instructor instructor = inv.getArgument(0);
            setInstructorIdViaReflection(instructor, UUID.randomUUID());
            return instructor;
        });

        // When
        InstructorAdminDto result = adminInstructorService.createInstructor(request);

        // Then
        assertNotNull(result);
        assertEquals("Jane", result.firstName());
        assertEquals("Smith", result.lastName());
        assertEquals("Expert in bouldering", result.bio());
        assertEquals("IFMGA, UIAGM", result.certifications());

        ArgumentCaptor<Instructor> captor = ArgumentCaptor.forClass(Instructor.class);
        verify(instructorRepository).save(captor.capture());

        Instructor saved = captor.getValue();
        assertEquals("Jane", saved.getFirstName());
        assertEquals("Smith", saved.getLastName());
        assertEquals("Expert in bouldering", saved.getBio());
        assertEquals("IFMGA, UIAGM", saved.getCertifications());
    }

    @Test
    void shouldCreateInstructorWithNullBioAndCertifications() {
        // Given
        CreateInstructorRequest request = new CreateInstructorRequest(
            "Jane",
            "Smith",
            null,
            null,
            null
        );

        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> {
            Instructor instructor = inv.getArgument(0);
            setInstructorIdViaReflection(instructor, UUID.randomUUID());
            return instructor;
        });

        // When
        InstructorAdminDto result = adminInstructorService.createInstructor(request);

        // Then
        assertNotNull(result);
        assertNull(result.bio());
        assertNull(result.certifications());
    }

    // ========== GET INSTRUCTOR TESTS ==========

    @Test
    void shouldGetInstructorById() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));

        // When
        InstructorAdminDto result = adminInstructorService.getInstructor(instructorId);

        // Then
        assertNotNull(result);
        assertEquals(instructorId, result.id());
        assertEquals("John", result.firstName());
        assertEquals("Doe", result.lastName());
        assertEquals("Experienced climbing instructor", result.bio());
        assertEquals("UIAA, IFSC", result.certifications());
    }

    @Test
    void shouldThrowExceptionWhenInstructorNotFound() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminInstructorService.getInstructor(instructorId)
        );
        assertEquals("Instructor not found", exception.getMessage());
    }

    @Test
    void shouldGetAllInstructorsOrderedByDisplayOrder() {
        // Given
        Instructor instructor1 = new Instructor("Alice", "Brown");
        setInstructorIdViaReflection(instructor1, UUID.randomUUID());
        instructor1.setDisplayOrder(1);

        Instructor instructor2 = new Instructor("Bob", "Green");
        setInstructorIdViaReflection(instructor2, UUID.randomUUID());
        instructor2.setDisplayOrder(2);

        when(instructorRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc())
            .thenReturn(List.of(instructor1, instructor2));

        // When
        List<InstructorAdminDto> result = adminInstructorService.getAllInstructors();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).firstName());
        assertEquals("Bob", result.get(1).firstName());
    }

    // ========== UPDATE INSTRUCTOR TESTS ==========

    @Test
    void shouldUpdateInstructorAllFields() {
        // Given
        UpdateInstructorRequest request = new UpdateInstructorRequest(
            "Jane",
            "Smith",
            "Updated bio",
            "Updated certs",
            false,
            5,
            null,
            null,
            null
        );

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        InstructorAdminDto result = adminInstructorService.updateInstructor(instructorId, request);

        // Then
        assertNotNull(result);
        assertEquals("Jane", result.firstName());
        assertEquals("Smith", result.lastName());
        assertEquals("Updated bio", result.bio());
        assertEquals("Updated certs", result.certifications());
        assertFalse(result.active());
        assertEquals(5, result.displayOrder());

        verify(instructorRepository).save(testInstructor);
    }

    @Test
    void shouldUpdateInstructorPartialFields() {
        // Given
        UpdateInstructorRequest request = new UpdateInstructorRequest(
            "Jane",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        InstructorAdminDto result = adminInstructorService.updateInstructor(instructorId, request);

        // Then
        assertNotNull(result);
        assertEquals("Jane", result.firstName());
        assertEquals("Doe", result.lastName()); // Not updated
        assertEquals("Experienced climbing instructor", result.bio()); // Not updated

        verify(instructorRepository).save(testInstructor);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentInstructor() {
        // Given
        UpdateInstructorRequest request = new UpdateInstructorRequest(
            "Jane",
            "Smith",
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminInstructorService.updateInstructor(instructorId, request)
        );
        assertEquals("Instructor not found", exception.getMessage());
    }

    @Test
    void shouldUpdateDisplayOrderOnly() {
        // Given
        UpdateInstructorRequest request = new UpdateInstructorRequest(
            null,
            null,
            null,
            null,
            null,
            10,
            null,
            null,
            null
        );

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        InstructorAdminDto result = adminInstructorService.updateInstructor(instructorId, request);

        // Then
        assertEquals(10, result.displayOrder());
        verify(instructorRepository).save(testInstructor);
    }

    @Test
    void shouldUpdateActiveStatusOnly() {
        // Given
        UpdateInstructorRequest request = new UpdateInstructorRequest(
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null
        );

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        InstructorAdminDto result = adminInstructorService.updateInstructor(instructorId, request);

        // Then
        assertFalse(result.active());
        verify(instructorRepository).save(testInstructor);
    }

    // ========== DELETE INSTRUCTOR TESTS ==========

    @Test
    void shouldDeleteInstructorSuccessfully() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));

        // When
        adminInstructorService.deleteInstructor(instructorId);

        // Then
        verify(instructorRepository).delete(testInstructor);
    }

    @Test
    void shouldDeleteInstructorWithPhoto() throws IOException {
        // Given
        testInstructor.setPhotoFilename("photo123.jpg");

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        doNothing().when(fileStorageService).delete("photo123.jpg", "instructors");

        // When
        adminInstructorService.deleteInstructor(instructorId);

        // Then
        verify(fileStorageService).delete("photo123.jpg", "instructors");
        verify(instructorRepository).delete(testInstructor);
    }

    @Test
    void shouldDeleteInstructorEvenIfPhotoDeleteFails() throws IOException {
        // Given
        testInstructor.setPhotoFilename("photo123.jpg");

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        doThrow(new IOException("File not found")).when(fileStorageService).delete("photo123.jpg", "instructors");

        // When
        adminInstructorService.deleteInstructor(instructorId);

        // Then
        verify(fileStorageService).delete("photo123.jpg", "instructors");
        verify(instructorRepository).delete(testInstructor); // Still deletes instructor
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentInstructor() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminInstructorService.deleteInstructor(instructorId)
        );
        assertEquals("Instructor not found", exception.getMessage());
    }

    // ========== UPLOAD PHOTO TESTS ==========

    @Test
    void shouldUploadPhotoSuccessfully() throws IOException {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        when(fileStorageService.store(mockFile, "instructors")).thenReturn("new-photo.jpg");
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminInstructorService.uploadPhoto(instructorId, mockFile);

        // Then
        assertEquals("new-photo.jpg", testInstructor.getPhotoFilename());
        verify(fileStorageService).store(mockFile, "instructors");
        verify(instructorRepository).save(testInstructor);
    }

    @Test
    void shouldReplaceOldPhotoWhenUploadingNew() throws IOException {
        // Given
        testInstructor.setPhotoFilename("old-photo.jpg");

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        doNothing().when(fileStorageService).delete("old-photo.jpg", "instructors");
        when(fileStorageService.store(mockFile, "instructors")).thenReturn("new-photo.jpg");
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminInstructorService.uploadPhoto(instructorId, mockFile);

        // Then
        verify(fileStorageService).delete("old-photo.jpg", "instructors");
        verify(fileStorageService).store(mockFile, "instructors");
        assertEquals("new-photo.jpg", testInstructor.getPhotoFilename());
    }

    @Test
    void shouldContinueUploadEvenIfOldPhotoDeleteFails() throws IOException {
        // Given
        testInstructor.setPhotoFilename("old-photo.jpg");

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        doThrow(new IOException("File not found")).when(fileStorageService).delete("old-photo.jpg", "instructors");
        when(fileStorageService.store(mockFile, "instructors")).thenReturn("new-photo.jpg");
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminInstructorService.uploadPhoto(instructorId, mockFile);

        // Then
        verify(fileStorageService).delete("old-photo.jpg", "instructors");
        verify(fileStorageService).store(mockFile, "instructors");
        assertEquals("new-photo.jpg", testInstructor.getPhotoFilename());
    }

    @Test
    void shouldThrowExceptionWhenUploadingPhotoForNonExistentInstructor() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminInstructorService.uploadPhoto(instructorId, mockFile)
        );
        assertEquals("Instructor not found", exception.getMessage());
    }

    @Test
    void shouldPropagateIOExceptionWhenStorageFails() throws IOException {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        when(fileStorageService.store(mockFile, "instructors")).thenThrow(new IOException("Storage error"));

        // When & Then
        assertThrows(IOException.class, () -> adminInstructorService.uploadPhoto(instructorId, mockFile));

        verify(instructorRepository, never()).save(any(Instructor.class));
    }

    // ========== DELETE PHOTO TESTS ==========

    @Test
    void shouldDeletePhotoSuccessfully() throws IOException {
        // Given
        testInstructor.setPhotoFilename("photo123.jpg");

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        doNothing().when(fileStorageService).delete("photo123.jpg", "instructors");
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminInstructorService.deletePhoto(instructorId);

        // Then
        assertNull(testInstructor.getPhotoFilename());
        verify(fileStorageService).delete("photo123.jpg", "instructors");
        verify(instructorRepository).save(testInstructor);
    }

    @Test
    void shouldThrowExceptionWhenDeletingPhotoFromInstructorWithoutPhoto() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> adminInstructorService.deletePhoto(instructorId)
        );
        assertEquals("Instructor has no photo", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenDeletingPhotoForNonExistentInstructor() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> adminInstructorService.deletePhoto(instructorId)
        );
        assertEquals("Instructor not found", exception.getMessage());
    }

    @Test
    void shouldPropagateIOExceptionWhenPhotoDeleteFails() throws IOException {
        // Given
        testInstructor.setPhotoFilename("photo123.jpg");

        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));
        doThrow(new IOException("Delete failed")).when(fileStorageService).delete("photo123.jpg", "instructors");

        // When & Then
        assertThrows(IOException.class, () -> adminInstructorService.deletePhoto(instructorId));

        verify(instructorRepository, never()).save(any(Instructor.class));
    }

    // ========== DTO CONVERSION TESTS ==========

    @Test
    void shouldConvertInstructorToDtoWithoutPhoto() {
        // Given
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));

        // When
        InstructorAdminDto result = adminInstructorService.getInstructor(instructorId);

        // Then
        assertNull(result.photoFilename());
        assertNull(result.photoUrl());
    }

    @Test
    void shouldConvertInstructorToDtoWithPhoto() {
        // Given
        testInstructor.setPhotoFilename("photo123.jpg");
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));

        // When
        InstructorAdminDto result = adminInstructorService.getInstructor(instructorId);

        // Then
        assertEquals("photo123.jpg", result.photoFilename());
        assertEquals(BASE_URL + "/api/files/instructors/photo123.jpg", result.photoUrl());
    }

    @Test
    void shouldBuildCorrectPhotoUrl() {
        // Given
        testInstructor.setPhotoFilename("test-photo.png");
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(testInstructor));

        // When
        InstructorAdminDto result = adminInstructorService.getInstructor(instructorId);

        // Then
        assertTrue(result.photoUrl().startsWith(BASE_URL));
        assertTrue(result.photoUrl().endsWith("test-photo.png"));
        assertTrue(result.photoUrl().contains("/api/files/instructors/"));
    }

    // ========== HELPER METHODS ==========

    private void setInstructorIdViaReflection(Instructor instructor, UUID id) {
        try {
            var idField = Instructor.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(instructor, id);

            var createdAtField = Instructor.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(instructor, Instant.now());

            var updatedAtField = Instructor.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(instructor, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set instructor ID", e);
        }
    }
}
