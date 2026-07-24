import os
import googlemaps
from google.cloud.aiplatform import adk
from dotenv import load_dotenv

load_dotenv()

GMAPS_KEY = os.environ.get("GOOGLE_MAPS_API_KEY")
gmaps = googlemaps.Client(key=GMAPS_KEY) if GMAPS_KEY else None

@adk.tool(
    name="calculate_travel_times_tool",
    description="Calculates real-world driving distance and travel time from an origin to a list of destinations. Use this to verify if food can be delivered or collected within expiration limits and charity operating hours."
)
def calculate_travel_times_tool(origin: str, destinations: list[str]) -> dict:
    """
    Calculates distance and travel time.
    
    Args:
        origin (str): The Lat,Long coordinates of the donor (e.g. '51.5200,-0.1566').
        destinations (list[str]): A list of Lat,Long coordinates for charities (e.g. ['51.5313,-0.1261']).
        
    Returns:
        dict: A dictionary mapping each destination to its distance (miles) and travel time (minutes).
    """
    if not gmaps:
        # Fallback if no API key is provided during dev
        return {"error": "GOOGLE_MAPS_API_KEY is missing. Cannot calculate routes."}
        
    try:
        matrix = gmaps.distance_matrix(
            origins=[origin],
            destinations=destinations,
            mode="driving"
        )
        
        results = {}
        for idx, dest in enumerate(destinations):
            element = matrix['rows'][0]['elements'][idx]
            if element['status'] == 'OK':
                results[dest] = {
                    "distance_miles": round(element['distance']['value'] * 0.000621371, 1),
                    "travel_time_mins": round(element['duration']['value'] / 60)
                }
            else:
                results[dest] = {"error": f"Route not calculable: {element['status']}"}
                
        return results
    except Exception as e:
        return {"error": str(e)}
