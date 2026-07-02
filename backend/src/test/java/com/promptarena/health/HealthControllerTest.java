package com.promptarena.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthReturnsOkStatus() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }
}
