package com.soma.testwes.photo

import com.jayway.jsonpath.JsonPath
import com.soma.testwes.support.IntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PhotoUploadIntegrationTest : IntegrationTest() {

    @Test
    fun `요청한 파일 수만큼 presigned URL을 발급한다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "결혼식 본식")

        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/upload-urls")
                .session(session)
                .contentType("application/json")
                .content(
                    """
                    {"files":[
                      {"filename":"DSC_0001.JPG","contentType":"image/jpeg"},
                      {"filename":"DSC_0002.JPG","contentType":"image/jpeg"}
                    ]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uploads", hasSize<Any>(2)))
            // 프론트가 이 URL로 S3에 직접 PUT 한다. 서명이 붙어 있어야 한다.
            .andExpect(jsonPath("$.uploads[0].uploadUrl", containsString("X-Amz-Signature")))
            .andExpect(jsonPath("$.uploads[0].s3Key", containsString("galleries/$galleryId/")))
    }

    @Test
    fun `업로드 완료를 통보하면 상태가 바뀐다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "돌잔치")
        val photoIds = issueUploadUrls(session, galleryId, count = 3)

        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/complete")
                .session(session)
                .contentType("application/json")
                .content("""{"photoIds":$photoIds}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(3))

        mockMvc.perform(get("/api/galleries/$galleryId/photos/summary").session(session))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.uploaded").value(3))
    }

    @Test
    fun `남의 갤러리에는 업로드 URL을 발급받을 수 없다`() {
        val ownerSession = loginAs("owner")
        val galleryId = createGallery(ownerSession, "남의 갤러리")

        val strangerSession = loginAs("stranger")

        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/upload-urls")
                .session(strangerSession)
                .contentType("application/json")
                .content("""{"files":[{"filename":"a.jpg","contentType":"image/jpeg"}]}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `다른 갤러리의 사진 id를 섞어 완료 통보하면 400`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "내 갤러리")
        val otherGalleryId = createGallery(session, "다른 갤러리")
        val otherPhotoIds = issueUploadUrls(session, otherGalleryId, count = 1)

        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/complete")
                .session(session)
                .contentType("application/json")
                .content("""{"photoIds":$otherPhotoIds}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `파일 목록이 비어 있으면 400`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "빈 요청")

        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/upload-urls")
                .session(session)
                .contentType("application/json")
                .content("""{"files":[]}"""),
        ).andExpect(status().isBadRequest)
    }

    private fun loginAs(loginId: String): MockHttpSession {
        createPhotographer(loginId)
        return login(loginId)
    }

    private fun createGallery(session: MockHttpSession, name: String): Long {
        val body = mockMvc.perform(
            post("/api/galleries")
                .session(session)
                .contentType("application/json")
                .content("""{"name":"$name"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        return JsonPath.read<Int>(body, "$.id").toLong()
    }

    private fun issueUploadUrls(session: MockHttpSession, galleryId: Long, count: Int): List<Long> {
        val files = (1..count).joinToString(",") {
            """{"filename":"photo-$it.jpg","contentType":"image/jpeg"}"""
        }

        val body = mockMvc.perform(
            post("/api/galleries/$galleryId/photos/upload-urls")
                .session(session)
                .contentType("application/json")
                .content("""{"files":[$files]}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        return JsonPath.read<List<Int>>(body, "$.uploads[*].photoId").map { it.toLong() }
    }
}
