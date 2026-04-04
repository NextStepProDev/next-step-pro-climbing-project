package pl.nextsteppro.climbing.domain.instructor;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instructors")
public class Instructor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "photo_filename", length = 500)
    @Nullable
    private String photoFilename;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String bio;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String certifications;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "focal_point_x")
    @Nullable
    private Float focalPointX;

    @Column(name = "focal_point_y")
    @Nullable
    private Float focalPointY;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instructor() {}

    public Instructor(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Nullable
    public String getPhotoFilename() {
        return photoFilename;
    }

    @Nullable
    public String getBio() {
        return bio;
    }

    @Nullable
    public String getCertifications() {
        return certifications;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setPhotoFilename(@Nullable String photoFilename) {
        this.photoFilename = photoFilename;
    }

    public void setBio(@Nullable String bio) {
        this.bio = bio;
    }

    public void setCertifications(@Nullable String certifications) {
        this.certifications = certifications;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Nullable
    public Float getFocalPointX() {
        return focalPointX;
    }

    public void setFocalPointX(@Nullable Float focalPointX) {
        this.focalPointX = focalPointX;
    }

    @Nullable
    public Float getFocalPointY() {
        return focalPointY;
    }

    public void setFocalPointY(@Nullable Float focalPointY) {
        this.focalPointY = focalPointY;
    }
}
