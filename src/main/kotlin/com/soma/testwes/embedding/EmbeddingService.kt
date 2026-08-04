package com.soma.testwes.embedding

import com.soma.testwes.config.AppProperties
import com.soma.testwes.gallery.GalleryService
import com.soma.testwes.photo.PhotoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvocationType
import software.amazon.awssdk.services.lambda.model.InvokeRequest

/**
 * 갤러리 하나의 임베딩을 계산하라고 Lambda를 깨운다. 벡터를 만드는 일 자체는 이 서버가
 * 하지 않는다 — 이미지 바이트가 여기를 거치지 않는 것과 같은 이유다.
 *
 * 사진 한 장마다가 아니라 갤러리 단위로 한 번 부른다. S3 이벤트로 장당 트리거를 걸면
 * 수천 장 업로드가 Lambda 수천 개를 동시에 띄우고, 각자 커넥션을 열어 db.t4g.micro를
 * 고갈시킨다.
 */
// 클래스 단위 @Transactional을 걸지 않는다. 걸면 Lambda 호출이 트랜잭션 안에서 일어나
// 그동안 커넥션 하나를 붙잡고 있게 된다. 여기서 DB를 쓰는 것은 단일 count 하나뿐이고,
// 그건 리포지토리 호출 자체의 트랜잭션으로 충분하다.
@Service
class EmbeddingService(
    private val galleryService: GalleryService,
    private val photoRepository: PhotoRepository,
    private val lambdaClient: LambdaClient,
    private val properties: AppProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(galleryId: Long, photographerId: Long, force: Boolean): EmbeddingRunResult {
        galleryService.getOwned(galleryId, photographerId)

        val functionName = properties.embedding.functionName
        require(functionName.isNotBlank()) {
            "임베딩 함수가 설정되지 않았습니다. prod는 Parameter Store의 " +
                "app.embedding.function-name에서 오고, 로컬에는 함수가 없는 것이 정상입니다."
        }

        // 실제로 무엇을 계산할지는 Lambda가 같은 기준으로 다시 고른다. 여기서 세는 것은
        // 호출자에게 "몇 장이 대상인지"를 즉시 알려주기 위한 값일 뿐이다.
        val targets = if (force) {
            photoRepository.countByGalleryId(galleryId)
        } else {
            photoRepository.countByGalleryIdAndEmbeddingIsNull(galleryId)
        }

        // 갤러리 하나가 최대 15분까지 걸릴 수 있어 응답을 기다릴 수 없다. EVENT는 즉시
        // 202로 돌아오고, 진행 상황은 GET /photos/summary의 embedded 수로 확인한다.
        //
        // 페이로드를 문자열로 조립하는 게 안전한 이유: galleryId는 Long, force는 Boolean이라
        // 사용자 문자열이 끼어들 자리가 없다.
        val response = lambdaClient.invoke(
            InvokeRequest.builder()
                .functionName(functionName)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String("""{"galleryId":$galleryId,"force":$force}"""))
                .build(),
        )

        log.info(
            "임베딩 실행 요청: galleryId={}, force={}, 대상={}장, function={}, status={}",
            galleryId, force, targets, functionName, response.statusCode(),
        )

        return EmbeddingRunResult(galleryId = galleryId, targets = targets)
    }
}

data class EmbeddingRunResult(
    val galleryId: Long,
    /** 이번 실행이 채우려는 사진 수. 완료 여부는 GET /photos/summary로 확인한다. */
    val targets: Long,
)
