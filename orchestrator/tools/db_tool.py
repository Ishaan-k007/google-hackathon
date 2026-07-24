import json
import os

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
