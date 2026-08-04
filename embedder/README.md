# embedder

갤러리 하나의 사진을 DINOv2로 임베딩해 `photo.embedding`(pgvector `vector(768)`)에 적재한다.
Lambda로 배포되지만 **로컬에서도 같은 코드가 그대로 돈다** — 진입점만 다르다.

```
handler.py    Lambda      event {"galleryId": 1, "force": false}
__main__.py   로컬 CLI    python -m embedder --gallery-id 1
     └─────── 둘 다 job.run() 하나를 부른다
```

## 흐름

```
SELECT id, s3_key FROM photo
WHERE gallery_id = ? AND status <> 'PENDING' AND embedding IS NULL
  → S3 GET → HEIC 디코드 · EXIF 회전 · 리사이즈 → DINOv2(L2 정규화)
  → UPDATE photo SET embedding = ?, status = 'EMBEDDED'
```

- **재실행이 안전하다.** 기본 조건이 `embedding IS NULL`이라 중간에 죽어도 다시 부르면 남은
  것만 이어서 한다. 한 장이 실패해도 잡을 죽이지 않고 `failed`에 s3_key만 모아 돌려준다.
- **`--force`는 이미 채워진 것까지 다시 계산한다.** 모델이나 전처리를 바꿔 전량 재계산할 때만.
- **`PENDING`은 건너뛴다.** presigned URL만 발급되고 S3에 객체가 없을 수 있는 상태다.

## 로컬 실행

RDS는 퍼블릭 접근이 없으므로 SSM 포트 포워딩으로 터널을 먼저 연다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(cd ../../organic-agent-test-infra/environments/prod && terraform output -raw instance_id)" \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<rds-endpoint>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
```

다른 탭에서:

```bash
python -m venv .venv && source .venv/bin/activate
# torchvision까지 받아야 한다. transformers의 fast 이미지 프로세서가 요구한다.
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt

export DB_HOST=localhost DB_PORT=15432 DB_NAME=test_wes_db DB_USER=test_wes_admin
export DB_PASSWORD="$(aws ssm get-parameter --region ap-northeast-2 \
  --name /test-wes/prod/spring.datasource.password --with-decryption \
  --query Parameter.Value --output text)"
export S3_BUCKET="$(cd ../../organic-agent-test-infra/environments/prod && terraform output -raw s3_bucket)"

python -m embedder --gallery-id 1
```

맥에서는 `torch.backends.mps`가 잡혀 GPU로 돈다. Lambda는 CPU다.

## 빌드와 배포

이미지 태그·ECR 주소·첫 apply 순서는 인프라 레포 README의 "임베딩 파이프라인" 절에 있다.

```bash
docker buildx build --platform linux/amd64 --provenance=false --sbom=false \
  -t "$REPO:latest" --push .
```

세 플래그가 전부 필요하다.

- **`--platform linux/amd64`** — 맥에서 빌드하면 기본이 arm64다. Lambda 함수는 x86_64로
  만들어져 있어서 아키텍처가 다르면 실행 시점에 죽는다.
- **`--provenance=false --sbom=false`** — 이게 없으면 buildx가 attestation이 붙은 **manifest
  list**를 만들고, Lambda는 그걸 거절한다:

  ```
  InvalidParameterValueException: The image manifest, config or layer media type
  for the source image ... is not supported.
  ```

  이미지 내용과 무관한 포장 형식 문제인데 메시지가 그렇게 읽히지 않는다. Lambda는 단일
  아키텍처 Docker v2 매니페스트만 받는다.

## 환경변수

| 변수 | 기본값 | 비고 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | — | 필수. Lambda는 Terraform이 주입 |
| `S3_BUCKET` | — | 필수 |
| `EMBED_DIM` | `768` | `vector(n)` 컬럼과 `Photo.EMBEDDING_DIMENSION`과 셋이 같아야 한다 |
| `EMBED_BATCH_SIZE` | `8` | 모델에 한 번에 넣는 장수 |
| `EMBED_MODEL_ID` | `facebook/dinov2-base` | 바꾸면 이미지를 다시 빌드해야 한다(가중치가 구워져 있다) |
| `RESIZE_LONG_EDGE` | `1024` | 디코딩 직후 메모리를 누르는 용도 |
