import json
from kafka import KafkaProducer

# Spring's JsonDeserializer resolves the target type from this header (the same one
# Spring's JsonSerializer writes on Java→Java messages). event-service's consumer has
# no default type configured, so a header-less message could not be typed — we emit the
# FQCN header ourselves so the candidate deserializes into EventCandidateFoundEvent.
_TYPE_HEADER = [("__TypeId__", b"org.ngcvfb.eventhubkz.common.events.EventCandidateFoundEvent")]

def make_producer(bootstrap: str) -> KafkaProducer:
    return KafkaProducer(bootstrap_servers=bootstrap,
                         value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                         key_serializer=lambda k: (k or "").encode("utf-8"))

def publish_candidate(producer: KafkaProducer, candidate: dict) -> None:
    producer.send("event.candidate.found", key=candidate.get("sourceUrl") or "",
                  value=candidate, headers=_TYPE_HEADER)
    producer.flush()
