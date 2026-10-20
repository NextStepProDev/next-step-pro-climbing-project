package pl.nextsteppro.climbing.api.admin.video;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.api.admin.video.AdminVideoDtos.UpdateVideoRequest;
import pl.nextsteppro.climbing.domain.video.Video;
import pl.nextsteppro.climbing.domain.video.VideoRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An emptied field arrives as "" and must clear; a missing one (null) leaves the value alone. The
 * video editor could not remove an excerpt or a description at all: the panel turned "" into
 * undefined and the service skipped the null (audit 2026-10-20).
 */
class AdminVideoServiceTest {

    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final AdminVideoService service = new AdminVideoService(videoRepository);

    @Test
    void shouldClearTheExcerptAndContentSentEmpty() {
        Video video = video();

        service.updateVideo(UUID.randomUUID(), new UpdateVideoRequest(null, "", " ", null));

        assertNull(video.getExcerpt());
        assertNull(video.getContent());
    }

    @Test
    void shouldLeaveTheExcerptAndContentAloneWhenNotSent() {
        Video video = video();

        service.updateVideo(UUID.randomUUID(), new UpdateVideoRequest("New title", null, null, null));

        assertEquals("Zajawka", video.getExcerpt());
        assertEquals("Opis", video.getContent());
    }

    private Video video() {
        Video video = new Video("Film", "https://www.youtube.com/watch?v=abc");
        video.setExcerpt("Zajawka");
        video.setContent("Opis");
        when(videoRepository.findById(any())).thenReturn(Optional.of(video));
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> inv.getArgument(0));
        return video;
    }
}
