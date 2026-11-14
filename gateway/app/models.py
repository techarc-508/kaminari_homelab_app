from typing import Any, Dict, Optional

from pydantic import BaseModel, Field


class CommandEnvelopeIn(BaseModel):
    """Typed command envelope. Unknown fields are rejected outright."""

    model_config = {"populate_by_name": True, "extra": "forbid"}

    schema_: str = Field(alias="schema")
    command_id: str
    nonce: str
    issued_at: str
    expires_at: str
    requested_by: str
    target: str
    action: str
    args: Dict[str, str] = Field(default_factory=dict)
    reply_topic: str
    requires_confirmation: bool


class StepUpApproval(BaseModel):
    model_config = {"extra": "forbid"}

    challenge_id: str
    code: str


class CommandExecuteRequest(BaseModel):
    model_config = {"extra": "forbid"}

    envelope: CommandEnvelopeIn
    approval: Optional[StepUpApproval] = None


class CommandResultOut(BaseModel):
    mock: bool = False
    ok: bool
    summary: str
    detail: Dict[str, Any] = Field(default_factory=dict)
    task_id: Optional[str] = None


class StepUpChallengeRequest(BaseModel):
    model_config = {"extra": "forbid"}

    command_id: str
    action: str
    target: str
    args: Dict[str, str] = Field(default_factory=dict)


class StepUpConfirmRequest(BaseModel):
    model_config = {"extra": "forbid"}

    code: str


class StepUpEnrollResponse(BaseModel):
    secret: str
    otpauth_uri: str
    requires_confirmation: bool = True
    digits: int = 6
    period_seconds: int = 30


class StepUpStatusResponse(BaseModel):
    enrolled: bool
    pending_enrollment: bool
    step_up: bool
    challenge_ttl_seconds: int
    mutations_enabled: bool


class StepUpChallengeResponse(BaseModel):
    challenge_id: str
    request_id: str
    action: str
    target: str
    expires_at: str


class CommandExecuteResponse(BaseModel):
    status: str
    result: CommandResultOut
    expired: bool = False
    replayed: bool = False
    correlation_id: Optional[str] = None
    audit_id: str
    identity: str
