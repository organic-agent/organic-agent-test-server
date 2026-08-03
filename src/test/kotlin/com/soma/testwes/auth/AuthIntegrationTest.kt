package com.soma.testwes.auth

import com.soma.testwes.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthIntegrationTest : IntegrationTest() {

    @Test
    fun `DB에 있는 계정으로 로그인하면 세션이 생긴다`() {
        createPhotographer("hyungjun")

        val session = login("hyungjun")

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.loginId").value("hyungjun"))
    }

    @Test
    fun `비밀번호가 틀리면 401`() {
        createPhotographer("hyungjun")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("""{"loginId":"hyungjun","password":"wrong-password"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `없는 계정이면 401 - 가입 API가 없으므로 계정은 DB에만 있다`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("""{"loginId":"stranger","password":"$PASSWORD"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `로그인하지 않으면 갤러리 API에 접근할 수 없다`() {
        mockMvc.perform(get("/api/galleries"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `로그아웃하면 세션이 무효화된다`() {
        createPhotographer("hyungjun")
        val session = login("hyungjun")

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized)
    }
}
