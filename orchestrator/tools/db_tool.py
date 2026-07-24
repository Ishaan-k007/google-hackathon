import json
import os
from google.cloud.aiplatform import adk

@adk.tool(
    name="fetch_charities_tool",
    description="Fetches a list of all registered charities and their requirements from the database. Use this to find potential matches for surplus food."
)
def fetch_charities_tool() -> list:
    """
    Returns a list of dictionaries containing charity information, their locations,
    and their specific requirements for receiving food.
    """
    file_path = os.path.join(os.path.dirname(__file__), '..', 'data', 'charities.json')
    try:
        with open(file_path, 'r') as f:
            charities = json.load(f)
            return charities
    except Exception as e:
        return [{"error": f"Failed to fetch charities: {str(e)}"}]
