"""환경변수 하나로 모아 읽는다.

Lambda는 Terraform이 값을 넣어 주고(modules/app_stack/embedding.tf), 로컬 실행은
`.env`가 아니라 셸 환경에서 온다. DB 접속 정보를 Parameter Store가 아니라 환경변수로
받는 이유는 인프라 README의 "임베딩 파이프라인" 절에 있다 — 요약하면, NAT 없는 VPC에서
SSM을 읽으려면 시간당 과금되는 인터페이스 엔드포인트가 두 개 필요해진다.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str

    s3_bucket: str

    embed_dim: int
    batch_size: int
    model_id: str

    #: 임베딩 전에 줄이는 긴 변 길이. DINOv2가 실제로 보는 것은 224px이지만, 프로세서가
    #: 알아서 줄이므로 여기서는 디코딩 직후 메모리를 눌러 두는 목적이 크다. 원본 그대로
    #: 배치를 쌓으면 4천만 화소 몇 장으로 Lambda 메모리가 넘어간다.
    resize_long_edge: int

    @staticmethod
    def from_env() -> "Settings":
        return Settings(
            db_host=_required("DB_HOST"),
            db_port=int(os.environ.get("DB_PORT", "5432")),
            db_name=_required("DB_NAME"),
            db_user=_required("DB_USER"),
            db_password=_required("DB_PASSWORD"),
            s3_bucket=_required("S3_BUCKET"),
            embed_dim=int(os.environ.get("EMBED_DIM", "768")),
            batch_size=int(os.environ.get("EMBED_BATCH_SIZE", "8")),
            model_id=os.environ.get("EMBED_MODEL_ID", "facebook/dinov2-base"),
            resize_long_edge=int(os.environ.get("RESIZE_LONG_EDGE", "1024")),
        )


def _required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        # 기동 시점에 죽는 편이 낫다. 늦게 발견하면 이미 사진 절반을 처리한 뒤다.
        raise RuntimeError(f"환경변수 {name}이(가) 비어 있습니다")
    return value
