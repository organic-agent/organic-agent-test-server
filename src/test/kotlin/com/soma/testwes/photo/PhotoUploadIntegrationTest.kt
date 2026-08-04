package com.soma.testwes.photo

import com.jayway.jsonpath.JsonPath
import com.soma.testwes.support.IntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
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
    fun `업로드를 마친 사진은 서명된 조회 URL과 함께 목록에 나온다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "조회 화면")
        val photoIds = issueUploadUrls(session, galleryId, count = 2)
        completeUpload(session, galleryId, photoIds)

        mockMvc.perform(get("/api/galleries/$galleryId/photos").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos", hasSize<Any>(2)))
            .andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.hasNext").value(false))
            // 버킷이 비공개라 이 URL이 브라우저가 이미지를 받을 유일한 통로다.
            .andExpect(jsonPath("$.photos[0].viewUrl", containsString("X-Amz-Signature")))
            .andExpect(jsonPath("$.photos[0].s3Key", containsString("galleries/$galleryId/")))
            .andExpect(jsonPath("$.photos[0].originalFilename").value("photo-1.jpg"))
    }

    @Test
    fun `아직 올라오지 않은 사진에는 조회 URL이 없다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "발급만 한 갤러리")
        issueUploadUrls(session, galleryId, count = 1)

        // S3에 객체가 없는 상태라 URL을 주면 프론트가 깨진 이미지를 그린다.
        mockMvc.perform(get("/api/galleries/$galleryId/photos").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos[0].status").value("PENDING"))
            .andExpect(jsonPath("$.photos[0].viewUrl", nullValue()))
    }

    @Test
    fun `status로 걸러 받을 수 있다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "섞인 갤러리")
        val photoIds = issueUploadUrls(session, galleryId, count = 3)
        completeUpload(session, galleryId, photoIds.take(2))

        mockMvc.perform(get("/api/galleries/$galleryId/photos?status=UPLOADED").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos", hasSize<Any>(2)))
            .andExpect(jsonPath("$.totalCount").value(2))
    }

    @Test
    fun `페이지를 나눠 받으면 다음 페이지가 있음을 알려준다`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "수천 장 갤러리")
        issueUploadUrls(session, galleryId, count = 3)

        mockMvc.perform(get("/api/galleries/$galleryId/photos?page=0&size=2").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photos", hasSize<Any>(2)))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.hasNext").value(true))

        mockMvc.perform(get("/api/galleries/$galleryId/photos?page=1&size=2").session(session))
            .andExpect(jsonPath("$.photos", hasSize<Any>(1)))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `한 번에 받을 수 있는 개수를 넘기면 400`() {
        val session = loginAs("hyungjun")
        val galleryId = createGallery(session, "과한 요청")

        mockMvc.perform(get("/api/galleries/$galleryId/photos?size=1001").session(session))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `남의 갤러리 사진 목록은 볼 수 없다`() {
        val ownerSession = loginAs("owner")
        val galleryId = createGallery(ownerSession, "남의 갤러리")

        val strangerSession = loginAs("stranger")

        mockMvc.perform(get("/api/galleries/$galleryId/photos").session(strangerSession))
            .andExpect(status().isForbidden)
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

    private fun completeUpload(session: MockHttpSession, galleryId: Long, photoIds: List<Long>) {
        mockMvc.perform(
            post("/api/galleries/$galleryId/photos/complete")
                .session(session)
                .contentType("application/json")
                .content("""{"photoIds":$photoIds}"""),
        ).andExpect(status().isOk)
    }
}
