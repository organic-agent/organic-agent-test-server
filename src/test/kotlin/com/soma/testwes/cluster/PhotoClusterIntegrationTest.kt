package com.soma.testwes.cluster

import com.soma.testwes.gallery.Gallery
import com.soma.testwes.photo.Photo
import com.soma.testwes.photographer.Photographer
import com.soma.testwes.support.IntegrationTest
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PhotoClusterIntegrationTest : IntegrationTest() {

    @Test
    fun `유사도 임계값을 넘는 사진끼리 한 묶음이 된다`() {
        val photographer = createPhotographer("hyungjun")
        val gallery = createGallery(photographer)

        // 같은 방향(코사인 유사도 0.998) 두 장 + 직교하는 한 장.
        savePhoto(gallery, "a.jpg", unitVector(0))
        savePhoto(gallery, "b.jpg", vectorOf(0 to 1.0f, 1 to 0.05f))
        savePhoto(gallery, "c.jpg", unitVector(5))

        val session = login("hyungjun")

        mockMvc.perform(get("/api/galleries/${gallery.requiredId}/clusters?threshold=0.9").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.threshold").value(0.9))
            .andExpect(jsonPath("$.clusters", hasSize<Any>(2)))
            // 큰 묶음이 앞에 온다.
            .andExpect(jsonPath("$.clusters[0]", hasSize<Any>(2)))
            .andExpect(jsonPath("$.clusters[1]", hasSize<Any>(1)))
    }

    @Test
    fun `임계값을 올리면 묶음이 더 잘게 쪼개진다`() {
        val photographer = createPhotographer("hyungjun")
        val gallery = createGallery(photographer)

        // a-b 유사도 0.8. 임계값 0.9에서는 갈라지고 0.75에서는 묶인다.
        savePhoto(gallery, "a.jpg", unitVector(0))
        savePhoto(gallery, "b.jpg", vectorOf(0 to 0.8f, 1 to 0.6f))

        val session = login("hyungjun")

        mockMvc.perform(get("/api/galleries/${gallery.requiredId}/clusters?threshold=0.9").session(session))
            .andExpect(jsonPath("$.clusters", hasSize<Any>(2)))

        mockMvc.perform(get("/api/galleries/${gallery.requiredId}/clusters?threshold=0.75").session(session))
            .andExpect(jsonPath("$.clusters", hasSize<Any>(1)))
    }

    @Test
    fun `직접 닮지 않아도 중간 사진을 통해 이어지면 같은 묶음이다`() {
        val photographer = createPhotographer("hyungjun")
        val gallery = createGallery(photographer)

        // a-b 0.8, b-c 0.6, a-c 0.0. 임계값 0.55면 a-b-c가 사슬로 이어진다.
        savePhoto(gallery, "a.jpg", unitVector(0))
        savePhoto(gallery, "b.jpg", vectorOf(0 to 0.8f, 1 to 0.6f))
        savePhoto(gallery, "c.jpg", unitVector(1))

        val session = login("hyungjun")

        mockMvc.perform(get("/api/galleries/${gallery.requiredId}/clusters?threshold=0.55").session(session))
            .andExpect(jsonPath("$.clusters", hasSize<Any>(1)))
            .andExpect(jsonPath("$.clusters[0]", hasSize<Any>(3)))
    }

    @Test
    fun `임베딩이 없는 사진은 묶이지 않고 따로 센다`() {
        val photographer = createPhotographer("hyungjun")
        val gallery = createGallery(photographer)

        savePhoto(gallery, "a.jpg", unitVector(0))
        savePhoto(gallery, "no-embedding.jpg", embedding = null)

        val session = login("hyungjun")

        mockMvc.perform(get("/api/galleries/${gallery.requiredId}/clusters").session(session))
            .andExpect(jsonPath("$.clusters", hasSize<Any>(1)))
            .andExpect(jsonPath("$.unclassified").value(1))
    }

    @Test
    fun `남의 갤러리는 클러스터링할 수 없다`() {
        val owner = createPhotographer("owner")
        val gallery = createGallery(owner)
        createPhotographer("stranger")

        val strangerSession: MockHttpSession = login("stranger")

        mockMvc.perform(get("/api/galleries/${gallery.requiredId}/clusters").session(strangerSession))
            .andExpect(status().isForbidden)
    }

    private fun createGallery(photographer: Photographer): Gallery =
        galleryRepository.save(Gallery(photographer = photographer, name = "테스트 갤러리"))

    private fun savePhoto(gallery: Gallery, filename: String, embedding: FloatArray?): Photo {
        val photo = Photo(
            gallery = gallery,
            s3Key = "galleries/${gallery.requiredId}/$filename",
            originalFilename = filename,
            contentType = "image/jpeg",
        )
        embedding?.let { photo.applyEmbedding(it) }
        return photoRepository.save(photo)
    }

    private fun unitVector(index: Int): FloatArray = vectorOf(index to 1.0f)

    private fun vectorOf(vararg components: Pair<Int, Float>): FloatArray =
        FloatArray(Photo.EMBEDDING_DIMENSION).apply {
            components.forEach { (index, value) -> this[index] = value }
        }
}
