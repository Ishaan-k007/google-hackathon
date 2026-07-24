import os
from fastapi import FastAPI
from google.cloud.aiplatform import a2a
from agents.orchestrator.agent import orchestrator_agent

# Initialize FastAPI app
app = FastAPI(title="Food Rescue Orchestrator API")

# Initialize A2A Server with our agent
a2a_server = a2a.A2AServer(agent=orchestrator_agent)

@app.post("/v1/tasks")
async def handle_orchestration_task(request: a2a.TaskRequest):
    """
    Receives an A2A TaskRequest (containing the Supermarket payload), 
    processes it through the Gemini Agent, and returns an A2A TaskResponse 
    with the Distribution Plan.
    """
    response = await a2a_server.process_task(request)
    return response

@app.get("/agent-card")
def get_agent_card():
    """
    Exposes capabilities, supported schemas, and service metadata 
    for system discovery.
    """
    return a2a_server.get_agent_card()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
