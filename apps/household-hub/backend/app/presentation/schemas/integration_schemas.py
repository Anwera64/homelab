from datetime import datetime
from typing import Any, Dict, List, Literal, Optional
from pydantic import BaseModel, ConfigDict, Field, field_validator


class CalendarCredentialCreate(BaseModel):
    provider: Literal["caldav", "google_caldav", "apple_icloud"]
    url: str = Field(..., min_length=8, max_length=512)
    username: str = Field(..., min_length=1, max_length=255)
    password: str = Field(..., min_length=1, max_length=256)
    calendar_name: Optional[str] = Field("Default", max_length=128)

    @field_validator("url")
    @classmethod
    def validate_caldav_url(cls, v: str) -> str:
        v = v.strip()
        if not (v.startswith("http://") or v.startswith("https://")):
            raise ValueError("Calendar URL must use http:// or https:// scheme.")
        lower = v.lower()
        if "169.254.169.254" in lower or "metadata.google.internal" in lower:
            raise ValueError("Target calendar URL is not permitted.")
        return v


class CalendarCredentialRead(BaseModel):
    id: str
    user_id: str
    provider: str
    url: str
    username: str
    calendar_name: str
    is_active: bool
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class CalendarEventCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    start_time: datetime
    end_time: datetime
    description: Optional[str] = Field("", max_length=65536)
    location: Optional[str] = Field("", max_length=512)
    is_all_day: Optional[bool] = False


class CalendarEventUpdate(BaseModel):
    title: Optional[str] = Field(None, max_length=255)
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    description: Optional[str] = Field(None, max_length=65536)
    location: Optional[str] = Field(None, max_length=512)
    is_all_day: Optional[bool] = None


class CalendarEventRead(BaseModel):
    id: str
    title: str
    start_time: datetime
    end_time: datetime
    description: str
    location: str
    is_all_day: bool
    calendar_name: str

    model_config = ConfigDict(from_attributes=True)


class SearchQueryRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=1000)
    fresh: Optional[bool] = False
    limit: Optional[int] = Field(10, ge=1, le=50)


class SearchResultItemResponse(BaseModel):
    title: str
    url: str
    snippet: str
    engine: str
    score: float


class SearchResultResponse(BaseModel):
    query: str
    category: str
    total_results: int
    is_cached: bool
    results: List[SearchResultItemResponse]


class DocumentSectionResponse(BaseModel):
    heading: str
    level: int
    content: str
    page_number: int
    is_citation: bool


class DocumentParseResponse(BaseModel):
    filename: str
    title: str
    author: str
    page_count: int
    sections: List[DocumentSectionResponse]
    citations: List[str]
    plain_text: str


class DocumentSaveRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    content: str = Field(..., max_length=5_000_000)
    action: Optional[Literal["create", "append", "replace"]] = "create"
    space_id: Optional[str] = None
    format: Optional[str] = Field("markdown", max_length=32)


class DocumentReadResponse(BaseModel):
    id: str
    user_id: str
    space_id: Optional[str] = None
    title: str
    content: str
    format: str
    version: int
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class ToolDefinitionResponse(BaseModel):
    name: str
    description: str
    parameters_schema: Dict[str, Any]


class ToolExecutionRequest(BaseModel):
    session_id: str = Field(..., min_length=1)
    tool_name: str
    arguments: Dict[str, Any]


class ToolExecutionResponse(BaseModel):
    tool_name: str
    success: bool
    data: Optional[Any] = None
    error: Optional[str] = None
