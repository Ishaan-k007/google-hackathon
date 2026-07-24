from pydantic import BaseModel, Field
from typing import List, Optional
import os

from google import genai
from google.genai import types
from dotenv import load_dotenv

from tools.db_tool import fetch_charities_tool
from tools.maps_tool import calculate_travel_times_tool

load_dotenv()

# We will initialize the client explicitly using the API key from .env
client = genai.Client(api_key=os.environ.get("GEMINI_API_KEY"))

# Pydantic schemas for structured output

class AllocatedItem(BaseModel):
    item_type: str = Field(description="The type of item allocated")
    quantity: int = Field(description="The amount allocated")

class Logistics(BaseModel):
    distance_miles: float = Field(description="The distance in miles between donor and charity")
    travel_time_mins: int = Field(description="Estimated driving time in minutes")

class CharityAllocation(BaseModel):
    charity_id: str = Field(description="The ID of the charity")
    charity_name: str = Field(description="The name of the charity")
    items_allocated: List[AllocatedItem] = Field(description="Items allocated to this charity")
    logistics: Logistics = Field(description="Logistics information for this allocation")

class UnallocatedItem(BaseModel):
    item_type: str = Field(description="The type of item that could not be allocated")
    quantity: int = Field(description="The remaining quantity")
    reason: str = Field(description="Reason why it couldn't be allocated (e.g. no capacity, closed, too far)")

class DistributionPlanSchema(BaseModel):
    plan_id: str = Field(description="A generated unique ID for this plan")
    status: str = Field(description="SUCCESS if at least one item was allocated, PARTIAL or FAILED otherwise")
    allocations: List[CharityAllocation] = Field(description="List of charities and their allocations")
    unallocated_items: List[UnallocatedItem] = Field(description="Items that could not be matched")

system_instruction = """
You are an autonomous logistics coordinator for a food rescue operation. 
Your goal is to distribute 100% of the given supermarket surplus to valid charities.

Workflow:
1. Use fetch_charities_tool() to discover all available charities.
2. Extract the locations of charities that might accept the food and use calculate_travel_times_tool() to verify distance and travel times from the donor.
3. Evaluate constraints:
   - dietary/allergen alignment
   - max capacity
   - travel time vs expiration limits
   - operating hours (can it be delivered/collected before they close?)
4. Split items among charities if necessary to ensure all stock is distributed.
5. Format the result strictly to the provided output schema. Do NOT hallucinate logistics, use the data from the tools.
"""

def generate_plan(prompt: str) -> dict:
    """
    Calls the Gemini API using the new genai client, passing the tools and forcing 
    the Pydantic schema as the structured output.
    """
    response = client.models.generate_content(
        model='gemini-3.5-flash',
        contents=prompt,
        config=types.GenerateContentConfig(
            system_instruction=system_instruction,
            tools=[fetch_charities_tool, calculate_travel_times_tool],
            response_mime_type="application/json",
            response_schema=DistributionPlanSchema,
            temperature=0.1
        )
    )
    
    import json
    return json.loads(response.text)

