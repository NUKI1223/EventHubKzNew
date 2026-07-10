import json
from kafka import KafkaProducer

def make_producer(bootstrap: str) -> KafkaProducer:
    return KafkaProducer(bootstrap_servers=bootstrap,
                         value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                         key_serializer=lambda k: (k or "").encode("utf-8"))

def publish_candidate(producer: KafkaProducer, candidate: dict) -> None:
    producer.send("event.candidate.found", key=candidate.get("sourceUrl") or "", value=candidate)
    producer.flush()
