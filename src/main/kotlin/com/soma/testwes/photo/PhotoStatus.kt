package com.soma.testwes.photo

enum class PhotoStatus {
    /** presigned URL만 발급된 상태. S3에 실제 객체가 없을 수 있다. */
    PENDING,

    /** 프론트가 업로드 완료를 통보한 상태. */
    UPLOADED,

    /** 임베딩까지 적재되어 클러스터링 대상이 된 상태. */
    EMBEDDED,
}
