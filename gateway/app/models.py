from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class ErrorResponse(BaseModel):
    error: str
    code: str = "invalid_request"


class NodeSummary(BaseModel):
    node: str
    status: str
    cpu: Optional[float] = None
    maxcpu: Optional[int] = None
    mem: Optional[int] = None
    maxmem: Optional[int] = None


class VmSummary(BaseModel):
    vmid: int
    name: str
    status: str
    node: str
    type: str = "qemu"


class StorageSummary(BaseModel):
    storage: str
    type: str
    content: Optional[str] = None
    total: Optional[int] = None
    used: Optional[int] = None
    avail: Optional[int] = None


class CommandEnvelopeIn(BaseModel):
    schema: str = Field(alias="schema")
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

    model_config = {"populate_by_name": True}


class CommandExecuteRequest(BaseModel):
    envelope: CommandEnvelopeIn
    approval_token: Optional[str] = None


class CommandResultOut(BaseModel):
    mock: bool = False
    ok: bool
    summary: str
    detail: Dict[str, Any] = Field(default_factory=dict)
    task_id: Optional[str] = None


class CommandExecuteResponse(BaseModel):
    status: str
    result: CommandResultOut
    expired: bool = False
