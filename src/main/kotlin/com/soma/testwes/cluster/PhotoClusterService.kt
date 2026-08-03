package com.soma.testwes.cluster

import com.soma.testwes.config.AppProperties
import com.soma.testwes.gallery.GalleryService
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프론트가 준 유사도 임계값 이상으로 닮은 사진들을 한 덩어리로 묶는다.
 *
 * 임계값을 넘는 쌍을 간선으로 보고 연결 요소를 찾는 방식이라, A-B가 닮고 B-C가 닮으면
 * A-C가 임계값 미만이어도 같은 묶음이 된다. "같은 인물/장면 묶기"에는 이 동작이 맞다.
 */
@Service
class PhotoClusterService(
    private val jdbcClient: JdbcClient,
    private val galleryService: GalleryService,
    private val properties: AppProperties,
) {

    @Transactional(readOnly = true)
    fun cluster(galleryId: Long, photographerId: Long, threshold: Double?): ClusterResult {
        galleryService.getOwned(galleryId, photographerId)

        val similarity = threshold ?: properties.cluster.defaultThreshold
        require(similarity in 0.0..1.0) { "유사도는 0.0 ~ 1.0 사이여야 합니다 (요청: $similarity)" }

        val members = loadEmbeddedPhotos(galleryId)
        if (members.isEmpty()) {
            return ClusterResult(threshold = similarity, clusters = emptyList(), unclassified = countWithoutEmbedding(galleryId))
        }

        val indexById = members.withIndex().associate { (index, photo) -> photo.id to index }
        val unionFind = UnionFind(members.size)

        // pgvector의 <=> 는 코사인 '거리'다. 유사도 0.9 = 거리 0.1.
        loadSimilarPairs(galleryId, 1.0 - similarity).forEach { (left, right) ->
            unionFind.union(indexById.getValue(left), indexById.getValue(right))
        }

        val clusters = members.indices
            .groupBy { unionFind.find(it) }
            .values
            .map { indices -> indices.map { members[it].s3Key } }
            .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.first() })

        return ClusterResult(
            threshold = similarity,
            clusters = clusters,
            unclassified = countWithoutEmbedding(galleryId),
        )
    }

    private fun loadEmbeddedPhotos(galleryId: Long): List<PhotoRef> =
        jdbcClient.sql(
            """
            SELECT id, s3_key
            FROM photo
            WHERE gallery_id = :galleryId AND embedding IS NOT NULL
            ORDER BY id
            """.trimIndent(),
        )
            .param("galleryId", galleryId)
            .query { rs, _ -> PhotoRef(rs.getLong("id"), rs.getString("s3_key")) }
            .list()

    /**
     * 갤러리 안의 모든 쌍을 정확히 비교한다(N²). 근사 최근접(HNSW)으로 바꾸면 훨씬 빠르지만
     * 누락된 간선이 곧 잘못 쪼개진 묶음이 되므로, 갤러리 규모가 커지기 전까지는 정확도를 택한다.
     */
    private fun loadSimilarPairs(galleryId: Long, maxDistance: Double): List<Pair<Long, Long>> =
        jdbcClient.sql(
            """
            SELECT a.id AS left_id, b.id AS right_id
            FROM photo a
                     JOIN photo b
                          ON b.gallery_id = a.gallery_id
                              AND b.id > a.id
                              AND b.embedding IS NOT NULL
            WHERE a.gallery_id = :galleryId
              AND a.embedding IS NOT NULL
              AND (a.embedding <=> b.embedding) <= :maxDistance
            """.trimIndent(),
        )
            .param("galleryId", galleryId)
            .param("maxDistance", maxDistance)
            .query { rs, _ -> rs.getLong("left_id") to rs.getLong("right_id") }
            .list()

    private fun countWithoutEmbedding(galleryId: Long): Long =
        jdbcClient.sql("SELECT count(*) FROM photo WHERE gallery_id = :galleryId AND embedding IS NULL")
            .param("galleryId", galleryId)
            .query(Long::class.java)
            .single()

    private data class PhotoRef(val id: Long, val s3Key: String)
}

data class ClusterResult(
    val threshold: Double,
    val clusters: List<List<String>>,
    /** 아직 임베딩이 없어 어느 묶음에도 들어가지 못한 사진 수. */
    val unclassified: Long,
)

private class UnionFind(size: Int) {

    private val parent = IntArray(size) { it }

    fun find(node: Int): Int {
        var root = node
        while (parent[root] != root) root = parent[root]

        // 경로 압축. 수천 장 × 수만 간선에서 find가 반복 호출된다.
        var current = node
        while (parent[current] != root) {
            val next = parent[current]
            parent[current] = root
            current = next
        }
        return root
    }

    fun union(left: Int, right: Int) {
        val leftRoot = find(left)
        val rightRoot = find(right)
        if (leftRoot != rightRoot) parent[rightRoot] = leftRoot
    }
}
