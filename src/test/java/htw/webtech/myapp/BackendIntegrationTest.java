package htw.webtech.myapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import htw.webtech.myapp.persistence.repository.AdEntryRepository;
import htw.webtech.myapp.persistence.repository.NotificationEntryRepository;
import htw.webtech.myapp.persistence.repository.SessionTokenRepository;
import htw.webtech.myapp.persistence.repository.UserEntryRepository;
import htw.webtech.myapp.rest.model.AdRequest;
import htw.webtech.myapp.rest.model.LoginRequest;
import htw.webtech.myapp.rest.model.PurchaseRequest;
import htw.webtech.myapp.rest.model.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop"
        }
)
@AutoConfigureMockMvc
class BackendIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private AdEntryRepository adRepo;
    @Autowired private NotificationEntryRepository notificationRepo;
    @Autowired private UserEntryRepository userRepo;
    @Autowired private SessionTokenRepository sessionTokenRepo;

    @BeforeEach
    void resetDbAndUploads() throws Exception {
        notificationRepo.deleteAll();
        adRepo.deleteAll();
        userRepo.deleteAll();
        sessionTokenRepo.deleteAll();

        deleteDirectoryQuietly(Paths.get("uploads"));
    }

    private static void deleteDirectoryQuietly(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "_" + System.nanoTime() + "@test.de";
    }

    private String register(String email, String password) throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(email);
        reg.setPassword(password);

        String body = mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);

        String body = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private String registerAndLogin(String email, String password) throws Exception {
        register(email, password);
        return login(email, password);
    }

    private long createAd(String token, String brand, String size, String price) throws Exception {
        AdRequest req = new AdRequest();
        req.setBrand(brand);
        req.setSize(size);
        req.setPrice(price);

        String expectedPrice = price.replace(',', '.');

        String body = mockMvc.perform(post("/api/ads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.brand").value(brand))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.price").value(expectedPrice))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void registerThenLoginOk() throws Exception {
        String email = uniqueEmail("u");
        register(email, "1234");

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest() {{
                            setEmail(email);
                            setPassword("1234");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registerDuplicateFails() throws Exception {
        String email = uniqueEmail("dup");

        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setPassword("1234");

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginWrongPasswordFails() throws Exception {
        String email = uniqueEmail("wp");
        registerAndLogin(email, "1234");

        LoginRequest bad = new LoginRequest();
        bad.setEmail(email);
        bad.setPassword("xxxx");

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAdsEmpty() throws Exception {
        mockMvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createAdUnauthorized() throws Exception {
        AdRequest req = new AdRequest();
        req.setBrand("Nike");
        req.setSize("42");
        req.setPrice("50");

        mockMvc.perform(post("/api/ads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAdValidationFails() throws Exception {
        String token = registerAndLogin(uniqueEmail("c"), "1234");

        AdRequest bad = new AdRequest();
        bad.setBrand("");
        bad.setSize("5");
        bad.setPrice("10");

        mockMvc.perform(post("/api/ads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAndListAdsOk() throws Exception {
        String token = registerAndLogin(uniqueEmail("seller"), "1234");
        createAd(token, "Nike", "42", "50");

        mockMvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].brand").value("Nike"))
                .andExpect(jsonPath("$[0].sold").value(false));
    }

    @Test
    void updateNotFound() throws Exception {
        String token = registerAndLogin(uniqueEmail("d"), "1234");

        AdRequest req = new AdRequest();
        req.setBrand("Nike");
        req.setSize("42");
        req.setPrice("50");

        mockMvc.perform(put("/api/ads/999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateOwnAdOk() throws Exception {
        String token = registerAndLogin(uniqueEmail("own"), "1234");
        long id = createAd(token, "Nike", "42", "50");

        AdRequest edit = new AdRequest();
        edit.setBrand("Adidas");
        edit.setSize("43");
        edit.setPrice("60");

        mockMvc.perform(put("/api/ads/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.brand").value("Adidas"))
                .andExpect(jsonPath("$.size").value("43"))
                .andExpect(jsonPath("$.price").value("60"));
    }

    @Test
    void editForeignAdForbidden() throws Exception {
        String a = registerAndLogin(uniqueEmail("a1"), "1234");
        String b = registerAndLogin(uniqueEmail("b1"), "1234");

        long id = createAd(a, "Nike", "42", "50");

        AdRequest edit = new AdRequest();
        edit.setBrand("Adidas");
        edit.setSize("43");
        edit.setPrice("60");

        mockMvc.perform(put("/api/ads/" + id)
                        .header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOwnAdNoContent() throws Exception {
        String token = registerAndLogin(uniqueEmail("del"), "1234");
        long id = createAd(token, "Puma", "41", "20");

        mockMvc.perform(delete("/api/ads/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteForeignAdForbidden() throws Exception {
        String owner = registerAndLogin(uniqueEmail("own2"), "1234");
        String other = registerAndLogin(uniqueEmail("other2"), "1234");

        long id = createAd(owner, "Puma", "41", "20");

        mockMvc.perform(delete("/api/ads/" + id)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test
    void imageUploadAndDelete() throws Exception {
        String token = registerAndLogin(uniqueEmail("img"), "1234");
        long id = createAd(token, "Boot", "41", "20");

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", "x".getBytes()
        );

        mockMvc.perform(multipart("/api/ads/" + id + "/image")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath", notNullValue()));

        mockMvc.perform(delete("/api/ads/" + id + "/image")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").doesNotExist());
    }

    @Test
    void imageUploadForeignAdForbidden() throws Exception {
        String owner = registerAndLogin(uniqueEmail("imgown"), "1234");
        String other = registerAndLogin(uniqueEmail("imgother"), "1234");
        long id = createAd(owner, "Boot", "41", "20");

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", "x".getBytes()
        );

        mockMvc.perform(multipart("/api/ads/" + id + "/image")
                        .file(file)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchaseEdgeCases() throws Exception {
        String seller = registerAndLogin(uniqueEmail("s"), "1234");
        String buyer = registerAndLogin(uniqueEmail("b"), "1234");

        long id = createAd(seller, "Vans", "44", "70");

        PurchaseRequest own = new PurchaseRequest();
        own.setAdIds(List.of(id));

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(own)))
                .andExpect(status().isForbidden());

        PurchaseRequest empty = new PurchaseRequest();
        empty.setAdIds(List.of());

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchaseOkAndSecondPurchaseConflicts() throws Exception {
        String seller = registerAndLogin(uniqueEmail("sell"), "1234");
        String buyer1 = registerAndLogin(uniqueEmail("buy1"), "1234");
        String buyer2 = registerAndLogin(uniqueEmail("buy2"), "1234");

        long id = createAd(seller, "Nike", "42", "50");

        PurchaseRequest p = new PurchaseRequest();
        p.setAdIds(List.of(id));

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + buyer1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + buyer2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isConflict());
    }

    @Test
    void notificationsEmpty() throws Exception {
        String token = registerAndLogin(uniqueEmail("n"), "1234");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void notificationsMarkRead() throws Exception {
        String seller = registerAndLogin(uniqueEmail("n1"), "1234");
        String buyer = registerAndLogin(uniqueEmail("n2"), "1234");

        long id = createAd(seller, "Reebok", "40", "30");

        PurchaseRequest p = new PurchaseRequest();
        p.setAdIds(List.of(id));

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());

        String list = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode arr = objectMapper.readTree(list);
        long notifId = arr.get(0).get("id").asLong();

        mockMvc.perform(post("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void notificationsMarkReadForbiddenForOtherUser() throws Exception {
        String seller = registerAndLogin(uniqueEmail("nseller"), "1234");
        String buyer = registerAndLogin(uniqueEmail("nbuyer"), "1234");
        String attacker = registerAndLogin(uniqueEmail("natt"), "1234");

        long id = createAd(seller, "Reebok", "40", "30");

        PurchaseRequest p = new PurchaseRequest();
        p.setAdIds(List.of(id));

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());

        String list = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + seller))
                .andReturn().getResponse().getContentAsString();

        long notifId = objectMapper.readTree(list).get(0).get("id").asLong();

        mockMvc.perform(post("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + attacker))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileEmpty() throws Exception {
        String token = registerAndLogin(uniqueEmail("p"), "1234");

        mockMvc.perform(get("/api/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldCount").value(0))
                .andExpect(jsonPath("$.boughtCount").value(0))
                .andExpect(jsonPath("$.revenueTotal").value(0))
                .andExpect(jsonPath("$.spentTotal").value(0));
    }

    @Test
    void profileCalculatesRevenueAndSpent() throws Exception {
        String sellerEmail = uniqueEmail("pseller");
        String buyerEmail = uniqueEmail("pbuyer");

        String sellerToken = registerAndLogin(sellerEmail, "1234");
        String buyerToken = registerAndLogin(buyerEmail, "1234");

        long id = createAd(sellerToken, "Nike", "42", "12.50");

        PurchaseRequest p = new PurchaseRequest();
        p.setAdIds(List.of(id));

        mockMvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/profile")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldCount").value(1))
                .andExpect(jsonPath("$.revenueTotal").value(12.50));

        mockMvc.perform(get("/api/profile")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boughtCount").value(1))
                .andExpect(jsonPath("$.spentTotal").value(12.50));
    }
}
