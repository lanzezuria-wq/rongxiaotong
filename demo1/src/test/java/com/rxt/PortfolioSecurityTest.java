package com.rxt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rxt.security.rate-limit.max-requests=200")
@AutoConfigureMockMvc
@Transactional
class PortfolioSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void changingPasswordRevokesOtherDevicesAndAllowsNewLogin() throws Exception {
        String oldToken = login("13900000004", "123456");
        mvc.perform(put("/api/user/password").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"123456\",\"newPassword\":\"portfolio123\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/user/info").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        String newToken = login("13900000004", "portfolio123");
        mvc.perform(get("/api/user/info").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedContentTypeIsAClientError() throws Exception {
        mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("phone=13900000004&password=123456"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void normalStatusManagementCannotBypassExpertReview() throws Exception {
        String admin = login("13900000000", "123456");
        mvc.perform(put("/api/admin/user/4/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditLogIsQueryableByAdminAndContainsNoRequestSecrets() throws Exception {
        String admin = login("13900000000", "123456");
        mvc.perform(post("/api/admin/dict").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"industry\",\"code\":\"PORTFOLIO_TEST\",\"name\":\"审计测试\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"));
        String body = mvc.perform(get("/api/admin/logs").param("keyword", "新增字典项")
                        .header("Authorization", "Bearer " + admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("123456"));
        assertFalse(body.contains(admin));
        String farmer = login("13900000004", "123456");
        mvc.perform(get("/api/admin/logs").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isForbidden());
    }

    @Test
    void referencedDictionaryCodesCannotBeRemovedOrRenamed() throws Exception {
        String admin = login("13900000000", "123456");
        Long id = jdbc.queryForObject("SELECT id FROM t_dict WHERE type = 'product_category' AND code = 'GRAIN'", Long.class);
        mvc.perform(delete("/api/admin/dict/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/dict/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"product_category\",\"code\":\"CHANGED_GRAIN\",\"name\":\"改名测试\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenablingAnAccountDoesNotReviveItsOldTokens() throws Exception {
        String farmer = login("13900000004", "123456");
        String admin = login("13900000000", "123456");
        Long id = jdbc.queryForObject("SELECT id FROM t_user WHERE phone = '13900000004'", Long.class);
        for (int status : new int[]{3, 1}) {
            mvc.perform(put("/api/admin/user/" + id + "/status").header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":" + status + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/user/info").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isUnauthorized());
    }

    private String login(String phone, String password) throws Exception {
        String body = mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), body);
        return matcher.group(1);
    }
}
