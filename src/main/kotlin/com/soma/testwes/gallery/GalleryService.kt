package com.soma.testwes.gallery

import com.soma.testwes.photographer.PhotographerRepository
import com.soma.testwes.support.ForbiddenException
import com.soma.testwes.support.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GalleryService(
    private val galleryRepository: GalleryRepository,
    private val photographerRepository: PhotographerRepository,
) {

    @Transactional
    fun create(photographerId: Long, name: String): Gallery {
        val photographer = photographerRepository.findById(photographerId)
            .orElseThrow { NotFoundException("사진작가를 찾을 수 없습니다") }
        return galleryRepository.save(Gallery(photographer = photographer, name = name))
    }

    fun findAllOwnedBy(photographerId: Long): List<Gallery> =
        galleryRepository.findAllByPhotographerIdOrderByIdDesc(photographerId)

    /**
     * 남의 갤러리에 사진을 넣거나 클러스터링 결과를 받아가지 못하게 막는다.
     * 갤러리를 다루는 모든 진입점이 이걸 먼저 통과해야 한다.
     */
    fun getOwned(galleryId: Long, photographerId: Long): Gallery {
        val gallery = galleryRepository.findById(galleryId)
            .orElseThrow { NotFoundException("갤러리를 찾을 수 없습니다") }

        // 존재 여부를 숨길 이유는 없어 404 대신 403으로 구분해 돌려준다.
        if (gallery.photographer.requiredId != photographerId) {
            throw ForbiddenException("이 갤러리에 대한 권한이 없습니다")
        }
        return gallery
    }
}
