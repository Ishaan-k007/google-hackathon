let currentDonationId = null;

function collectChecked(containerId) {
  return Array.from(document.querySelectorAll("#" + containerId + " input:checked")).map(i => i.value);
}

function setChecked(containerId, values) {
  if (!values) return;
  const lower = values.map(v => String(v).toLowerCase());
  document.querySelectorAll("#" + containerId + " input").forEach(i => {
    i.checked = lower.includes(i.value.toLowerCase());
  });
}

document.getElementById("interpretBtn").addEventListener("click", async () => {
  const statusEl = document.getElementById("interpretStatus");
  statusEl.textContent = "Interpreting…";
  try {
    const result = await api("/api/gemini/interpret", {
      method: "POST",
      body: JSON.stringify({ role: "supermarket", text: document.getElementById("nlText").value })
    });
    if (result.quantity) document.getElementById("quantity").value = result.quantity;
    if (result.dietaryTypes) setChecked("dietaryTypes", result.dietaryTypes);
    if (result.allergens) setChecked("allergens", result.allergens);
    if (result.storageType) document.getElementById("storageType").value = result.storageType;
    if (result.availableFrom) document.getElementById("availableFrom").value = result.availableFrom;
    if (result.expiresAt) document.getElementById("expiresAt").value = result.expiresAt;

    statusEl.textContent = result._source === "gemini" ? "✓ Interpreted with Gemini" : "✓ Interpreted (offline fallback)";

    const banner = document.getElementById("clarificationBanner");
    if (result.clarificationQuestion) {
      banner.style.display = "flex";
      banner.className = "clarification-banner";
      banner.innerHTML = "<span>✨</span><span><strong>Gemini noticed something missing:</strong> " + escapeHtml(result.clarificationQuestion) + "</span>";
    }
  } catch (e) {
    statusEl.textContent = "Could not interpret text.";
    console.error(e);
  }
});

document.getElementById("donationForm").addEventListener("submit", async (evt) => {
  evt.preventDefault();
  const donation = {
    supermarketName: document.getElementById("supermarketName").value,
    pickupLocation: document.getElementById("pickupLocation").value,
    foodDescription: document.getElementById("foodDescription").value,
    quantity: parseInt(document.getElementById("quantity").value, 10),
    dietaryTypes: collectChecked("dietaryTypes"),
    allergens: collectChecked("allergens"),
    storageType: document.getElementById("storageType").value,
    availableFrom: document.getElementById("availableFrom").value,
    expiresAt: document.getElementById("expiresAt").value,
    additionalNotes: document.getElementById("additionalNotes").value
  };

  try {
    const saved = await api("/api/donations", { method: "POST", body: JSON.stringify(donation) });
    currentDonationId = saved.id;

    const statusLine = document.getElementById("statusLine");
    statusLine.style.display = "flex";
    statusLine.className = "status-line pulse";
    statusLine.textContent = "Supermarket Agent is searching for suitable charities and transport.";

    const banner = document.getElementById("clarificationBanner");
    if (saved.clarificationQuestion) {
      banner.style.display = "flex";
      banner.className = "clarification-banner";
      banner.innerHTML = "<span>✨</span><span><strong>Gemini noticed something missing:</strong> " + escapeHtml(saved.clarificationQuestion) + "</span>";
    } else {
      banner.style.display = "none";
    }

    document.getElementById("feedCard").style.display = "block";
    startAgentFeedPolling(document.getElementById("feed"));
    pollForPlan();
  } catch (e) {
    alert("Could not submit donation: " + e.message);
  }
});

function pollForPlan() {
  setInterval(async () => {
    if (!currentDonationId) return;
    try {
      const plan = await api("/api/rescue-plan");
      if (plan && plan.donationId === currentDonationId) {
        const statusLine = document.getElementById("statusLine");
        statusLine.className = "status-line";
        if (plan.status === "CONFIRMED") {
          statusLine.textContent = "Rescue confirmed! " + plan.driverName + " is collecting " + plan.allocatedMeals + " meals.";
        } else if (plan.safetyPassed) {
          statusLine.textContent = "A match has been proposed - waiting for confirmation on the coordinator dashboard.";
        } else {
          statusLine.className = "status-line";
          statusLine.style.background = "var(--critical-soft)";
          statusLine.style.color = "var(--critical)";
          statusLine.textContent = "The proposed match was rejected by the Safety Agent - see details below.";
        }
        renderPlanCard(plan, document.getElementById("planContainer"), {});
      }
    } catch (e) { /* ignore transient errors while polling */ }
  }, 2000);
}
