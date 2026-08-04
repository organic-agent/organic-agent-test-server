"""DINOv2 장면 임베딩.

organic-agent-ai-v3의 `app/ingest/embedder.py`를 이 잡에 맞게 옮긴 것이다. 그쪽은 자체
스키마와 프리뷰 파이프라인에 얽혀 있어 통째로 재사용하기 어렵고, 실제로 공유할 값이 있는
부분은 이 클래스뿐이다.

CLS 토큰을 L2 정규화해서 돌려준다. pgvector의 `<=>`(코사인 거리)는 스케일에 무관하므로
서버의 클러스터링 쿼리는 정규화 없이도 맞게 동작하지만, ai-v3 설계 전체가 "내적 = 코사인
유사도" 위에 서 있어서 나중에 트리 방식이나 내적 기반 인덱스로 옮길 때 정규화 안 된 값이
섞여 있으면 원인을 찾기 어려워진다. 그래서 적재 시점에 못박는다.
"""

from __future__ import annotations

import logging
from functools import lru_cache

import numpy as np
from PIL import Image

from embedder.config import Settings

log = logging.getLogger(__name__)


class DinoEmbedder:
    def __init__(self, model_id: str, expected_dim: int) -> None:
        # torch는 여기서만 import한다. 임포트 자체가 수 초 걸려서, 모델을 안 쓰는 경로가
        # 그 값을 치르지 않게 한다.
        import torch
        from transformers import AutoImageProcessor, AutoModel

        self._torch = torch
        self.model_id = model_id
        self.expected_dim = expected_dim

        # Lambda에는 GPU가 없다. 로컬 맥에서 같은 코드를 돌릴 때만 MPS가 잡힌다.
        if torch.cuda.is_available():
            self.device = "cuda"
        elif torch.backends.mps.is_available():
            self.device = "mps"
        else:
            self.device = "cpu"

        log.info("모델 로드: %s (device=%s)", model_id, self.device)
        self.processor = AutoImageProcessor.from_pretrained(model_id)
        self.model = AutoModel.from_pretrained(model_id).to(self.device).eval()

    def encode(self, images: list[Image.Image]) -> np.ndarray:
        torch = self._torch
        with torch.inference_mode():
            batch = self.processor(images=images, return_tensors="pt").to(self.device)
            cls = self.model(**batch).last_hidden_state[:, 0]
            cls = torch.nn.functional.normalize(cls, dim=-1)
            vectors = cls.float().cpu().numpy()

        # 모델과 DB 컬럼이 어긋나면 UPDATE에서 걸리긴 하지만, 그때는 이미 배치 하나를
        # 통째로 계산한 뒤다. 첫 배치에서 바로 멈추는 편이 낫다.
        if vectors.shape[1] != self.expected_dim:
            raise RuntimeError(
                f"임베딩 차원이 설정과 다릅니다: 모델 {vectors.shape[1]}, 기대값 {self.expected_dim}"
            )
        return vectors


@lru_cache(maxsize=1)
def get_embedder(model_id: str, expected_dim: int) -> DinoEmbedder:
    """웜 스타트에서 재사용하려고 프로세스당 하나만 만든다."""
    return DinoEmbedder(model_id, expected_dim)


def load_from(settings: Settings) -> DinoEmbedder:
    return get_embedder(settings.model_id, settings.embed_dim)
