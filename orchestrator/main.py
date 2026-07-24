import os
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Dict, Any

# Import the generate_plan function which acts as our agent
from agents.orchestrator.agent import generate_plan

# Initialize FastAPI app
app = FastAPI(title="Food Rescue Orchestrator API")

class MatchRequest(BaseModel):
    # This expects exactly the Supermarket payload we defined
    donor_id: str
    donor_name: str
    location: str
    available_from: str
    surplus_items: list[Dict[str, Any]]

@app.post("/v1/tasks")
async def handle_orchestration_task(request: MatchRequest):
    """
    Receives a POST request with the Supermarket payload, 
    processes it through the Gemini Agent, and returns the 
    Distribution Plan JSON.
    """
    # Convert the payload to a string prompt for the agent
    prompt = f"Find matches for this supermarket surplus:\n{request.model_dump_json(indent=2)}"
    
    # Run the agent
    response_dict = generate_plan(prompt)
    
    # Return the structured JSON dict
    return response_dict

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
