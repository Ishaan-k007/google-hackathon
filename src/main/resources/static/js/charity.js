let currentCharityRequestId = null;

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
      body: JSON.stringify({ role: "charity", text: document.getElementById("nlText").value })
    });
    if (result.requestedQuantity) document.getElementById("requestedQuantity").value = result.requestedQuantity;
    if (result.acceptedDietaryTypes) setChecked("acceptedDietaryTypes", result.acceptedDietaryTypes);
    if (result.rejectedAllergens) setChecked("rejectedAllergens", result.rejectedAllergens);
    if (result.latestDeliveryTime) document.getElementById("latestDeliveryTime").value = result.latestDeliveryTime;
    if (typeof result.canCollect === "boolean") {
      document.querySelector('input[name="canCollect"][value="' + (result.canCollect ? "yes" : "no") + '"]').checked = true;
    }

    statusEl.textContent = result._source === "gemini" ? "✓ Interpreted with Gemini" : "✓ Interpreted (offline fallback)";

    const preview = document.getElementById("constraintsPreview");
    let html = "";
    if (result.hardConstraints && result.hardConstraints.length) {
      html += '<div class="hint" style="margin-top:10px;"><strong>Hard constraints:</strong> ' + result.hardConstraints.map(escapeHtml).join(" · ") + '</div>';
    }
    if (result.preferences && result.preferences.length) {
      html += '<div class="hint"><strong>Preferences:</strong> ' + result.preferences.map(escapeHtml).join(" · ") + '</div>';
    }
    if (result.clarificationQuestion) {
      html += '<div class="clarification-banner" style="margin-top:10px;"><span>✨</span><span>' + escapeHtml(result.clarificationQuestion) + '</span></div>';
    }
    preview.innerHTML = html;
  } catch (e) {
    statusEl.textContent = "Could not interpret text.";
    console.error(e);
  }
});

document.getElementById("charityForm").addEventListener("submit", async (evt) => {
  evt.preventDefault();
  const request = {
    charityName: document.getElementById("charityName").value,
    deliveryLocation: document.getElementById("deliveryLocation").value,
    requestedQuantity: parseInt(document.getElementById("requestedQuantity").value, 10),
    acceptedDietaryTypes: collectChecked("acceptedDietaryTypes"),
    rejectedAllergens: collectChecked("rejectedAllergens"),
    latestDeliveryTime: document.getElementById("latestDeliveryTime").value,
    storageCapabilities: document.getElementById("storageCapabilities").value,
    canCollect: document.querySelector('input[name="canCollect"]:checked').value === "yes",
    additionalNotes: document.getElementById("additionalNotes").value
  };

  try {
    const saved = await api("/api/charity-requests", { method: "POST", body: JSON.stringify(request) });
    currentCharityRequestId = saved.id;

    const statusLine = document.getElementById("statusLine");
    statusLine.style.display = "flex";
    statusLine.className = "status-line pulse";
    statusLine.textContent = "Charity Agent is looking for a compatible donation.";

    document.getElementById("feedCard").style.display = "block";
    startAgentFeedPolling(document.getElementById("feed"));
    pollForPlan();
  } catch (e) {
    alert("Could not submit request: " + e.message);
  }
});

function pollForPlan() {
  setInterval(async () => {
    if (!currentCharityRequestId) return;
    try {
      const plan = await api("/api/rescue-plan");
      if (plan && plan.charityRequestId === currentCharityRequestId) {
        const statusLine = document.getElementById("statusLine");
        statusLine.className = "status-line";
        if (plan.status === "CONFIRMED") {
          statusLine.textContent = "Rescue confirmed! " + plan.allocatedMeals + " meals are on the way.";
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
