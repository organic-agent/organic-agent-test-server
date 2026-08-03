package com.soma.testwes.support

import com.soma.testwes.gallery.GalleryRepository
import com.soma.testwes.photo.PhotoRepository
import com.soma.testwes.photographer.Photographer
import com.soma.testwes.photographer.PhotographerRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var photographerRepository: PhotographerRepository

    @Autowired
    protected lateinit var galleryRepository: GalleryRepository

    @Autowired
    protected lateinit var photoRepository: PhotoRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    // 컨테이너를 JVM 단위로 재사용하므로 테스트끼리 데이터가 새지 않게 직접 지운다.
    // FK 때문에 사진 -> 갤러리 -> 작가 순서를 지켜야 한다.
    @BeforeEach
    fun clearAll() {
        photoRepository.deleteAllInBatch()
        galleryRepository.deleteAllInBatch()
        photographerRepository.deleteAllInBatch()
    }

    /** 가입 API가 없으므로 테스트도 운영과 같이 DB에 직접 만든다. */
    protected fun createPhotographer(loginId: String, password: String = PASSWORD): Photographer =
        photographerRepository.save(
            Photographer(
                loginId = loginId,
                password = passwordEncoder.encode(password)!!,
                name = "$loginId 작가",
            ),
        )

    /** 로그인 후 세션을 돌려준다. 이후 요청에 .session(...)으로 실어 보낸다. */
    protected fun login(loginId: String, password: String = PASSWORD): MockHttpSession {
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("""{"loginId":"$loginId","password":"$password"}"""),
        ).andReturn()

        return result.request.session as MockHttpSession
    }

    companion object {
        const val PASSWORD = "test-password-1234"

        // 클래스마다 컨테이너를 새로 띄우면 테스트가 느려져서 JVM 단위로 하나만 쓴다.
        // pgvector 확장이 들어 있는 이미지여야 V1__init.sql의 CREATE EXTENSION이 통과한다.
        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"),
        ).apply { start() }
    }
}
