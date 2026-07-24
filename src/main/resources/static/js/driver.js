let currentDriverId = null;

document.getElementById("interpretBtn").addEventListener("click", async () => {
  const statusEl = document.getElementById("interpretStatus");
  statusEl.textContent = "Interpreting…";
  try {
    const result = await api("/api/gemini/interpret", {
      method: "POST",
      body: JSON.stringify({ role: "driver", text: document.getElementById("nlText").value })
    });
    if (result.availableFrom) document.getElementById("availableFrom").value = result.availableFrom;
    if (result.availableUntil) document.getElementById("availableUntil").value = result.availableUntil;
    if (result.maximumDistanceMiles) document.getElementById("maximumDistanceMiles").value = result.maximumDistanceMiles;
    if (result.capacityMeals) document.getElementById("capacityMeals").value = result.capacityMeals;
    if (typeof result.hasInsulatedStorage === "boolean") document.getElementById("hasInsulatedStorage").checked = result.hasInsulatedStorage;

    statusEl.textContent = result._source === "gemini" ? "✓ Interpreted with Gemini" : "✓ Interpreted (offline fallback)";
  } catch (e) {
    statusEl.textContent = "Could not interpret text.";
    console.error(e);
  }
});

document.getElementById("driverForm").addEventListener("submit", async (evt) => {
  evt.preventDefault();
  const driver = {
    driverName: document.getElementById("driverName").value,
    startingLocation: document.getElementById("startingLocation").value,
    availableFrom: document.getElementById("availableFrom").value,
    availableUntil: document.getElementById("availableUntil").value || null,
    maximumDistanceMiles: parseFloat(document.getElementById("maximumDistanceMiles").value),
    capacityMeals: parseInt(document.getElementById("capacityMeals").value, 10),
    hasInsulatedStorage: document.getElementById("hasInsulatedStorage").checked,
    additionalNotes: document.getElementById("additionalNotes").value
  };

  try {
    const saved = await api("/api/drivers", { method: "POST", body: JSON.stringify(driver) });
    currentDriverId = saved.id;

    const statusLine = document.getElementById("statusLine");
    statusLine.style.display = "flex";
    statusLine.className = "status-line pulse";
    statusLine.textContent = "Driver Agent is searching for a suitable collection.";

    document.getElementById("feedCard").style.display = "block";
    startAgentFeedPolling(document.getElementById("feed"));
    pollForPlan();
  } catch (e) {
    alert("Could not submit availability: " + e.message);
  }
});

function pollForPlan() {
  setInterval(async () => {
    if (!currentDriverId) return;
    try {
      const plan = await api("/api/rescue-plan");
      if (plan && plan.driverAvailabilityId === currentDriverId) {
        const statusLine = document.getElementById("statusLine");
        statusLine.className = "status-line";
        if (plan.status === "CONFIRMED") {
          statusLine.textContent = "Rescue confirmed! Collect " + plan.allocatedMeals + " meals at " + plan.pickupTime + ".";
        } else if (plan.safetyPassed) {
          statusLine.textContent = "A match has been proposed - waiting for confirmation on the coordinator dashboard.";
        } else {
          statusLine.style.background = "var(--critical-soft)";
          statusLine.style.color = "var(--critical)";
          statusLine.textContent = "The proposed match was rejected by the Safety Agent - see details below.";
        }
        renderPlanCard(plan, document.getElementById("planContainer"), {});
      }
    } catch (e) { /* ignore transient errors while polling */ }
  }, 2000);
}
