-- 임베딩 모델을 DINOv2-base로 정하면서 컬럼 폭을 512 -> 768로 맞춘다.
--
-- ALTER TYPE으로 옮기지 않는 이유: pgvector에서 차원은 타입의 일부라 vector(512)와
-- vector(768)은 서로 캐스팅되지 않는다. 값을 보존할 방법이 아예 없다. 다행히 지금까지
-- 임베딩을 채운 적이 없어(파이프라인이 방금 생겼다) 잃을 데이터도 없다.
--
-- 모델을 또 바꿔 차원이 달라지면 이 파일이 아니라 새 마이그레이션을 추가하고,
-- Photo.EMBEDDING_DIMENSION과 인프라의 embedding_dimension 변수를 함께 옮겨야 한다.
-- 셋이 어긋나면 UPDATE가 DB에서 거절된다 -- 조용히 틀리지는 않는다는 뜻이다.
ALTER TABLE photo
    DROP COLUMN embedding;

ALTER TABLE photo
    ADD COLUMN embedding vector(768);
