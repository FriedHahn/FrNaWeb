package htw.webtech.myapp.rest.controller;

import htw.webtech.myapp.business.service.AuthService;
import htw.webtech.myapp.persistence.entity.AdEntry;
import htw.webtech.myapp.persistence.repository.AdEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/ads")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://frnawebfront2.onrender.com"
})
public class AdImageController {

    private final AdEntryRepository adRepo;
    private final AuthService authService;

    private static final String UPLOAD_DIR = "uploads/ads";

    public AdImageController(AdEntryRepository adRepo, AuthService authService) {
        this.adRepo = adRepo;
        this.authService = authService;
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadImage(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String email = authService.getEmailFromBearerHeader(authHeader);
            if (email == null) return ResponseEntity.status(401).body("Nicht eingeloggt");

            AdEntry ad = adRepo.findById(id).orElse(null);
            if (ad == null) return ResponseEntity.status(404).body("Anzeige nicht gefunden");

            if (!ad.getOwnerEmail().equalsIgnoreCase(email)) {
                return ResponseEntity.status(403).body("Nicht erlaubt");
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Keine Datei");
            }

            Files.createDirectories(Paths.get(UPLOAD_DIR));

            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            if (ext == null || ext.isBlank()) ext = "jpg";
            ext = ext.toLowerCase();

            if (!(ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp"))) {
                return ResponseEntity.badRequest().body("Nur jpg, jpeg, png, webp erlaubt");
            }

            if (ad.getImagePath() != null && !ad.getImagePath().isBlank()) {
                Path oldPath = Paths.get("uploads", ad.getImagePath().replaceFirst("^/uploads/", ""));
                Files.deleteIfExists(oldPath);
            }

            String filename = "ad_" + id + "." + ext;
            Path path = Paths.get(UPLOAD_DIR, filename);
            Files.write(path, file.getBytes());

            ad.setImagePath("/uploads/ads/" + filename);
            adRepo.save(ad);

            return ResponseEntity.ok(ad);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload fehlgeschlagen");
        }
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<?> deleteImage(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            String email = authService.getEmailFromBearerHeader(authHeader);
            if (email == null) return ResponseEntity.status(401).body("Nicht eingeloggt");

            AdEntry ad = adRepo.findById(id).orElse(null);
            if (ad == null) return ResponseEntity.status(404).body("Anzeige nicht gefunden");

            if (!ad.getOwnerEmail().equalsIgnoreCase(email)) {
                return ResponseEntity.status(403).body("Nicht erlaubt");
            }

            if (ad.getImagePath() != null && !ad.getImagePath().isBlank()) {
                Path oldPath = Paths.get("uploads", ad.getImagePath().replaceFirst("^/uploads/", ""));
                Files.deleteIfExists(oldPath);
            }

            ad.setImagePath(null);
            adRepo.save(ad);

            return ResponseEntity.ok(ad);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Loeschen fehlgeschlagen");
        }
    }
}
