package htw.webtech.myapp.rest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import htw.webtech.myapp.business.service.AuthService;
import htw.webtech.myapp.persistence.entity.AdEntry;
import htw.webtech.myapp.persistence.repository.AdEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ads")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://frnawebfront2.onrender.com"
})
public class AdImageController {

    private final AdEntryRepository adRepo;
    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            if (ext == null || ext.isBlank()) ext = "jpg";
            ext = ext.toLowerCase();

            if (!(ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp"))) {
                return ResponseEntity.badRequest().body("Nur jpg, jpeg, png, webp erlaubt");
            }

            String secureUrl = uploadToCloudinary(file.getBytes(), file.getOriginalFilename(), file.getContentType());

            ad.setImagePath(secureUrl);
            adRepo.save(ad);

            return ResponseEntity.ok(ad);
        } catch (IllegalStateException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
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

            ad.setImagePath(null);
            adRepo.save(ad);

            return ResponseEntity.ok(ad);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Loeschen fehlgeschlagen");
        }
    }

    private String uploadToCloudinary(byte[] fileBytes, String filename, String contentType) throws Exception {
        String cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
        String uploadPreset = System.getenv("CLOUDINARY_UPLOAD_PRESET");

        if (cloudName == null || cloudName.isBlank() || uploadPreset == null || uploadPreset.isBlank()) {
            throw new IllegalStateException("Cloudinary ENV fehlt (CLOUDINARY_CLOUD_NAME/CLOUDINARY_UPLOAD_PRESET)");
        }

        String boundary = "----Boundary" + UUID.randomUUID();
        String url = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";

        List<byte[]> parts = new ArrayList<>();

        parts.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add((uploadPreset + "\r\n").getBytes(StandardCharsets.UTF_8));

        String safeName = (filename == null || filename.isBlank()) ? "upload.jpg" : filename;
        String ct = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        parts.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(("Content-Type: " + ct + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(fileBytes);
        parts.add("\r\n".getBytes(StandardCharsets.UTF_8));

        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(parts))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Cloudinary Upload fehlgeschlagen (Status " + res.statusCode() + ")");
        }

        JsonNode json = objectMapper.readTree(res.body());
        String secureUrl = json.path("secure_url").asText();

        if (secureUrl == null || secureUrl.isBlank()) {
            throw new IllegalStateException("Cloudinary Response ohne secure_url");
        }

        return secureUrl;
    }
}
