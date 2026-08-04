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
    fun `사진 목록은 업로드된 것에만 서명된 조회 URL을 붙인다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "야외 촬영")
        val photoIds = issueUploadUrls(session, galleryId, count = 3)

        // 3장 중 2장만 업로드를 마쳤다고 통보한다.
        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/complete")
                .session(session)
                .contentType("application/json")
                .content("""{"photoIds":${photoIds.take(2)}}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/galleries/$galleryId/photos").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos", hasSize<Any>(3)))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.hasNext").value(false))
            // 프론트가 목록 재조회 시점을 이 값으로 정한다(app.s3.view-url-ttl = 15m).
            .andExpect(jsonPath("$.viewUrlTtlSeconds").value(900))
            .andExpect(jsonPath("$.photos[0].viewUrl", containsString("X-Amz-Signature")))
            // PENDING은 S3에 객체가 없을 수 있어 서명해 주지 않는다.
            .andExpect(jsonPath("$.photos[2].status").value("PENDING"))
            .andExpect(jsonPath("$.photos[2].viewUrl").doesNotExist())
    }

    @Test
    fun `사진 목록은 status로 거를 수 있다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "필터")
        val photoIds = issueUploadUrls(session, galleryId, count = 3)

        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/complete")
                .session(session)
                .contentType("application/json")
                .content("""{"photoIds":${photoIds.take(2)}}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/galleries/$galleryId/photos").session(session).param("status", "UPLOADED"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos", hasSize<Any>(2)))
            .andExpect(jsonPath("$.totalCount").value(2))
    }

    @Test
    fun `페이지 크기가 상한을 넘으면 400`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "페이지")

        mockMvc.perform(
            get("/api/galleries/$galleryId/photos").session(session).param("size", "1001"),
        ).andExpect(status().isBadRequest)
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
