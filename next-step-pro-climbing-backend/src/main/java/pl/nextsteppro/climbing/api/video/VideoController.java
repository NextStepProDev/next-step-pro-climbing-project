package pl.nextsteppro.climbing.api.video;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.video.VideoDtos.VideoDto;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@Tag(name = "Videos", description = "Publiczne filmy")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @Operation(summary = "Pobierz wszystkie opublikowane filmy")
    @GetMapping
    public ResponseEntity<List<VideoDto>> getAll() {
        return ResponseEntity.ok(videoService.getAllPublished());
    }
}
