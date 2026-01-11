package htw.webtech.myapp;

import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integrationstests für dein Backend über MockMvc.
 * Diese Tests decken die wichtigsten Endpunkte ab (5 bis 10 Tests total).
 *
 * Voraussetzungen:
 * - spring-boot-starter-test ist im Projekt
 * - Test DB (typisch H2) ist verfügbar
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BackendIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @BeforeEach
    void setup() {
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private String registerAndGetToken(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token", not(isEmptyOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = om.readTree(body);
        return node.get("token").asText();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token", not(isEmptyOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = om.readTree(body);
        return node.get("token").asText();
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private long createAd(String token, String brand, String size, String price) throws Exception {
        String body = mvc.perform(post("/api/ads")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("brand", brand, "size", size, "price", price))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.brand").value(brand))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.price").value(price))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return om.readTree(body).get("id").asLong();
    }

    @Test
    void register_returnsSuccessAndToken() throws Exception {
        mvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "a@test.de", "password", "1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        registerAndGetToken("b@test.de", "1234");

        mvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "b@test.de", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.message", not(isEmptyOrNullString())));
    }

    @Test
    void getAds_returnsOnlyUnsoldAds() throws Exception {
        String sellerToken = registerAndGetToken("seller@test.de", "1234");
        long adId = createAd(sellerToken, "Nike", "42", "99.99");

        mvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$[0].id").value(adId))
                .andExpect(jsonPath("$[0].sold").value(false));
    }

    @Test
    void createAd_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/api/ads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("brand", "Adidas", "size", "43", "price", "120"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAd_withAuth_createsAndIsVisibleInList() throws Exception {
        String token = registerAndGetToken("c@test.de", "1234");
        long adId = createAd(token, "Puma", "42.5", "89.50");

        mvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) adId)))
                .andExpect(jsonPath("$[?(@.id==" + adId + ")].brand").value(hasItem("Puma")));
    }

    @Test
    void updateAd_byNonOwner_returns403() throws Exception {
        String ownerToken = registerAndGetToken("owner@test.de", "1234");
        String otherToken = registerAndGetToken("other@test.de", "1234");

        long adId = createAd(ownerToken, "Nike", "42", "99.99");

        mvc.perform(put("/api/ads/{id}", adId)
                        .header("Authorization", auth(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("brand", "Nike", "size", "42", "price", "79.99"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchase_createsNotificationForSeller_andAdBecomesSold() throws Exception {
        String sellerToken = registerAndGetToken("sell@test.de", "1234");
        String buyerToken = registerAndGetToken("buy@test.de", "1234");

        long adId = createAd(sellerToken, "New Balance", "44", "110");

        mvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", auth(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("adIds", List.of(adId)))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) adId))));

        mvc.perform(get("/api/notifications")
                        .header("Authorization", auth(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].message", containsString("verkauft")));
    }

    @Test
    void notification_markRead_removesFromUnreadList() throws Exception {
        String sellerToken = registerAndGetToken("sell2@test.de", "1234");
        String buyerToken = registerAndGetToken("buy2@test.de", "1234");

        long adId = createAd(sellerToken, "Asics", "43", "95");

        mvc.perform(post("/api/purchases/checkout")
                        .header("Authorization", auth(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("adIds", List.of(adId)))))
                .andExpect(status().isOk());

        String unreadBody = mvc.perform(get("/api/notifications")
                        .header("Authorization", auth(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long notifId = om.readTree(unreadBody).get(0).get("id").asLong();

        mvc.perform(post("/api/notifications/{id}/read", notifId)
                        .header("Authorization", auth(sellerToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notifications")
                        .header("Authorization", auth(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
