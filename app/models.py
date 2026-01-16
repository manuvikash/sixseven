from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any, Literal
from datetime import datetime
from uuid import uuid4


class JobEvent(BaseModel):
    ts: datetime
    level: Literal["info", "warning", "error"]
    message: str
    data: Optional[Dict[str, Any]] = None


class JobInput(BaseModel):
    command_text: str
    query_or_prompt: str
    params: Dict[str, Any] = Field(default_factory=dict)
    image_present: bool = False


class Job(BaseModel):
    job_id: str = Field(default_factory=lambda: str(uuid4()))
    session_id: Optional[str] = None
    type: Literal["research", "creative"]
    status: Literal["queued", "running", "succeeded", "failed", "cancelled"] = "queued"
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    input: JobInput
    progress: Optional[int] = None
    result: Optional[Dict[str, Any]] = None
    error: Optional[Dict[str, Any]] = None
    events: List[JobEvent] = Field(default_factory=list)
    cancelled: bool = False

    def add_event(self, level: str, message: str, data: Optional[Dict[str, Any]] = None):
        event = JobEvent(ts=datetime.utcnow(), level=level, message=message, data=data)
        self.events.append(event)
        # Cap events at 50
        if len(self.events) > 50:
            self.events = self.events[-50:]
        self.updated_at = datetime.utcnow()


class Session(BaseModel):
    session_id: str
    active_job_id: Optional[str] = None
    last_command_text: Optional[str] = None
    last_intent: Optional[str] = None
    last_updated_at: datetime = Field(default_factory=datetime.utcnow)


class CommandRequest(BaseModel):
    command_text: str
    image_base64: Optional[str] = None
    session_id: Optional[str] = None
    defaults: Dict[str, Any] = Field(default_factory=lambda: {
        "timezone": "America/Los_Angeles",
        "freepik_imagination": "vivid",
        "freepik_aspect_ratio": "original"
    })


class CommandResponse(BaseModel):
    intent: Literal["research", "creative", "status", "stop", "unknown"]
    message: str
    session_id: str
    job_id: Optional[str] = None
    status: Optional[str] = None
    active_job: Optional[Dict[str, Any]] = None
    cancelled_job_id: Optional[str] = None


class JobListQuery(BaseModel):
    session_id: Optional[str] = None
    type: Optional[Literal["research", "creative"]] = None
    status: Optional[Literal["queued", "running", "succeeded", "failed", "cancelled"]] = None
    limit: int = 20
