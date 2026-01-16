import asyncio
import re
from typing import Dict, Any, Optional
from uuid import uuid4
from app.models import Job, JobInput, Session, CommandRequest, CommandResponse
from app.store import JobStore
from app.agents.dialogue import DialogueAgent
from app.agents.research import ResearchAgent
from app.agents.creative import CreativeAgent
from app.agents.status import StatusAgent
from app.agents.cancel import CancellationAgent
from app.observability import get_logger

logger = get_logger(__name__)


class OrchestratorAgent:
    """Orchestrator coordinates all agents and manages job lifecycle."""
    
    def __init__(self, store: JobStore, observer=None):
        self.store = store
        self.observer = observer
        self.dialogue_agent = DialogueAgent()
        self.research_agent = ResearchAgent(store, observer)
        self.creative_agent = CreativeAgent(store, observer)
        self.status_agent = StatusAgent(store, self.dialogue_agent)
        self.cancel_agent = CancellationAgent(store)
    
    async def handle_command(self, request: CommandRequest) -> CommandResponse:
        """Main entry point - routes commands to appropriate workflows."""
        
        # Generate or use session_id
        session_id = request.session_id or str(uuid4())
        
        # Parse intent
        intent, parsed_data = self._parse_intent(request.command_text)
        
        logger.info(
            "intent_parsed",
            intent=intent,
            session_id=session_id,
            has_query=bool(parsed_data.get("query") or parsed_data.get("prompt"))
        )
        
        # Update session
        session = self.store.get_session(session_id)
        if not session:
            session = Session(session_id=session_id)
        session.last_command_text = request.command_text
        session.last_intent = intent
        self.store.update_session(session)
        
        # Route based on intent
        if intent == "research":
            return await self._handle_research(session, parsed_data, request)
        elif intent == "creative":
            return await self._handle_creative(session, parsed_data, request)
        elif intent == "status":
            return self._handle_status(session)
        elif intent == "stop":
            return self._handle_stop(session)
        else:
            return CommandResponse(
                intent="unknown",
                message="I didn't understand that command. Try 'research', 'imagine', 'status', or 'stop'.",
                session_id=session_id
            )
    
    def _parse_intent(self, command_text: str) -> tuple[str, Dict[str, Any]]:
        """Parse command text to determine intent and extract data."""
        text = command_text.strip().lower()
        
        # Research intent
        if text.startswith("research"):
            query = re.sub(r'^research\s*:?\s*', '', command_text.strip(), flags=re.IGNORECASE)
            query = re.sub(r'^this\s*:?\s*', '', query, flags=re.IGNORECASE).strip()
            return "research", {"query": query}
        
        # Creative intent
        if text.startswith("imagine"):
            prompt = re.sub(r'^imagine\s*:?\s*', '', command_text.strip(), flags=re.IGNORECASE)
            prompt = re.sub(r'^this\s*:?\s*', '', prompt, flags=re.IGNORECASE).strip()
            return "creative", {"prompt": prompt}
        
        # Status intent
        if text == "status":
            return "status", {}
        
        # Stop intent
        if text in ["stop", "cancel"]:
            return "stop", {}
        
        return "unknown", {}
    
    async def _handle_research(self, session: Session, parsed_data: Dict[str, Any],
                               request: CommandRequest) -> CommandResponse:
        """Handle research workflow."""
        query = parsed_data.get("query", "")
        
        if not query:
            return CommandResponse(
                intent="research",
                message="Please provide a research query.",
                session_id=session.session_id
            )
        
        # Create job
        job = Job(
            session_id=session.session_id,
            type="research",
            status="queued",
            input=JobInput(
                command_text=request.command_text,
                query_or_prompt=query,
                params=request.defaults
            )
        )
        self.store.create_job(job)
        
        # Notify observer
        if self.observer:
            self.observer.job_created(job)
        
        # Update session
        session.active_job_id = job.job_id
        self.store.update_session(session)
        
        # Start async execution
        asyncio.create_task(self._execute_research(job, request.defaults.get("timezone", "America/Los_Angeles")))
        
        return CommandResponse(
            intent="research",
            message=f"Starting research on: {query[:50]}...",
            session_id=session.session_id,
            job_id=job.job_id,
            status="queued"
        )
    
    async def _handle_creative(self, session: Session, parsed_data: Dict[str, Any],
                               request: CommandRequest) -> CommandResponse:
        """Handle creative workflow."""
        prompt = parsed_data.get("prompt", "")
        
        if not prompt:
            return CommandResponse(
                intent="creative",
                message="Please provide an image prompt.",
                session_id=session.session_id
            )
        
        if not request.image_base64:
            return CommandResponse(
                intent="creative",
                message="Please provide an image for creative tasks.",
                session_id=session.session_id
            )
        
        # Create job
        job = Job(
            session_id=session.session_id,
            type="creative",
            status="queued",
            input=JobInput(
                command_text=request.command_text,
                query_or_prompt=prompt,
                params=request.defaults,
                image_present=True
            )
        )
        self.store.create_job(job)
        
        # Notify observer
        if self.observer:
            self.observer.job_created(job)
        
        # Update session
        session.active_job_id = job.job_id
        self.store.update_session(session)
        
        # Start async execution
        asyncio.create_task(self._execute_creative(
            job, 
            request.image_base64,
            request.defaults.get("freepik_imagination", "vivid"),
            request.defaults.get("freepik_aspect_ratio", "original")
        ))
        
        return CommandResponse(
            intent="creative",
            message=f"Generating image: {prompt[:50]}...",
            session_id=session.session_id,
            job_id=job.job_id,
            status="queued"
        )
    
    def _handle_status(self, session: Session) -> CommandResponse:
        """Handle status query."""
        status_data = self.status_agent.get_status(session.session_id)
        
        return CommandResponse(
            intent="status",
            message=status_data["message"],
            session_id=session.session_id,
            active_job=status_data["active_job"]
        )
    
    def _handle_stop(self, session: Session) -> CommandResponse:
        """Handle stop/cancel command."""
        cancelled_job_id = self.cancel_agent.cancel_job(session.session_id)
        
        if cancelled_job_id:
            message = "Task cancelled."
        else:
            message = "No active task to cancel."
        
        return CommandResponse(
            intent="stop",
            message=message,
            session_id=session.session_id,
            cancelled_job_id=cancelled_job_id
        )
    
    async def _execute_research(self, job: Job, timezone: str):
        """Execute research job asynchronously."""
        try:
            if self.observer:
                self.observer.job_started(job)
            await self.research_agent.execute(job, timezone)
            if self.observer:
                self.observer.job_completed(job)
        except Exception as e:
            logger.error("research_execution_error", error=str(e), job_id=job.job_id, exc_info=True)
            job.status = "failed"
            job.error = {"message": str(e)}
            self.store.update_job(job)
            if self.observer:
                self.observer.job_completed(job)
    
    async def _execute_creative(self, job: Job, image_base64: str, 
                                imagination: str, aspect_ratio: str):
        """Execute creative job asynchronously."""
        try:
            if self.observer:
                self.observer.job_started(job)
            await self.creative_agent.execute(job, image_base64, imagination, aspect_ratio)
            if self.observer:
                self.observer.job_completed(job)
        except Exception as e:
            logger.error("creative_execution_error", error=str(e), job_id=job.job_id, exc_info=True)
            job.status = "failed"
            job.error = {"message": str(e)}
            self.store.update_job(job)
            if self.observer:
                self.observer.job_completed(job)
