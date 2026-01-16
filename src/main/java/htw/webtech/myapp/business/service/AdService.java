package htw.webtech.myapp.business.service;

import htw.webtech.myapp.persistence.entity.AdEntry;
import htw.webtech.myapp.persistence.entity.NotificationEntry;
import htw.webtech.myapp.persistence.repository.AdEntryRepository;
import htw.webtech.myapp.persistence.repository.NotificationEntryRepository;
import htw.webtech.myapp.rest.model.AdRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdService {

    private final AdEntryRepository adEntryRepository;
    private final NotificationEntryRepository notificationRepo;

    public AdService(AdEntryRepository adEntryRepository, NotificationEntryRepository notificationRepo) {
        this.adEntryRepository = adEntryRepository;
        this.notificationRepo = notificationRepo;
    }

    public List<AdEntry> getAllAvailableAds() {
        return adEntryRepository.findAllBySoldFalse();
    }

    public AdEntry createAd(AdRequest request, String ownerEmail) {
        normalizePrice(request);
        validateRequest(request);

        if (ownerEmail == null || ownerEmail.isBlank()) throw new IllegalArgumentException("UNAUTHORIZED");

        AdEntry ad = new AdEntry(
                request.getBrand().trim(),
                request.getSize().trim(),
                request.getPrice().trim(),
                ownerEmail.trim().toLowerCase()
        );
        return adEntryRepository.save(ad);
    }

    public AdEntry updateAd(Long id, AdRequest request, String ownerEmail) {
        normalizePrice(request);
        validateRequest(request);

        if (ownerEmail == null || ownerEmail.isBlank()) throw new IllegalArgumentException("UNAUTHORIZED");

        AdEntry existing = adEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

        if (existing.isSold()) {
            throw new IllegalArgumentException("SOLD");
        }

        if (!existing.getOwnerEmail().equalsIgnoreCase(ownerEmail.trim())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }

        existing.setBrand(request.getBrand().trim());
        existing.setSize(request.getSize().trim());
        existing.setPrice(request.getPrice().trim());

        return adEntryRepository.save(existing);
    }

    public void deleteAd(Long id, String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank()) throw new IllegalArgumentException("UNAUTHORIZED");

        AdEntry existing = adEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

        if (existing.isSold()) {
            throw new IllegalArgumentException("SOLD");
        }

        if (!existing.getOwnerEmail().equalsIgnoreCase(ownerEmail.trim())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }

        adEntryRepository.deleteById(id);
    }

    public void purchaseAds(List<Long> adIds, String buyerEmail) {
        if (buyerEmail == null || buyerEmail.isBlank()) throw new IllegalArgumentException("UNAUTHORIZED");
        if (adIds == null || adIds.isEmpty()) throw new IllegalArgumentException("INVALID");

        String buyer = buyerEmail.trim().toLowerCase();

        for (Long id : adIds) {
            AdEntry ad = adEntryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

            if (ad.isSold()) throw new IllegalArgumentException("SOLD");

            if (ad.getOwnerEmail().equalsIgnoreCase(buyer)) {
                throw new IllegalArgumentException("FORBIDDEN");
            }

            ad.setSold(true);
            ad.setBuyerEmail(buyer);
            adEntryRepository.save(ad);

            String msg = "Deine Anzeige \"" + ad.getBrand() + "\" wurde verkauft.";
            notificationRepo.save(new NotificationEntry(ad.getOwnerEmail(), msg));
        }
    }

    private void validateRequest(AdRequest request) {
        if (request == null) throw new IllegalArgumentException("INVALID");

        String brand = request.getBrand() == null ? "" : request.getBrand().trim();
        String size = request.getSize() == null ? "" : request.getSize().trim();
        String price = request.getPrice() == null ? "" : request.getPrice().trim();

        if (brand.isEmpty()) throw new IllegalArgumentException("INVALID");

        if (!size.matches("^\\d+(\\.5)?$")) throw new IllegalArgumentException("INVALID");

        double s;
        try {
            s = Double.parseDouble(size);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("INVALID");
        }

        if (s < 20 || s > 55) throw new IllegalArgumentException("INVALID");

        if (!price.matches("^\\d+(\\.\\d{1,2})?$")) throw new IllegalArgumentException("INVALID");
    }

    private void normalizePrice(AdRequest request) {
        if (request == null || request.getPrice() == null) return;

        String raw = request.getPrice().trim();
        if (raw.isEmpty()) return;

        // Komma als Dezimaltrenner zulassen
        raw = raw.replace(',', '.');

        request.setPrice(raw);
    }
}
