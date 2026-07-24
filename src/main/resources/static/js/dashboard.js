let feedTimer = null;
let lastMealsRescued = -1;

function setStatus(text) {
  document.getElementById("dashboardStatus").textContent = text;
}

function miniField(k, v) {
  return "<dt>" + escapeHtml(k) + "</dt><dd>" + escapeHtml(v) + "</dd>";
}

function renderDonationMini(d) {
  const el = document.getElementById("donationMini");
  if (!d) { el.innerHTML = '<div class="empty-state">No active donation yet.</div>'; return; }
  el.innerHTML = '<div class="mini-card">'
    + '<div class="title">' + escapeHtml(d.supermarketName) + '</div>'
    + '<div class="sub">' + escapeHtml(d.foodDescription || "") + '</div>'
    + '<dl>'
    + miniField("Meals", d.quantity)
    + miniField("Dietary", (d.dietaryTypes || []).join(", ") || "-")
    + miniField("Allergens declared", (d.allergens || []).join(", ") || "None confirmed")
    + miniField("Storage", d.storageType)
    + miniField("Window", (d.availableFrom || "-") + " – " + (d.expiresAt || "-"))
    + miniField("Status", d.status)
    + '</dl>'
    + (d.clarificationQuestion ? '<div class="clarification-banner" style="margin-top:10px;"><span>✨</span><span>' + escapeHtml(d.clarificationQuestion) + '</span></div>' : '')
    + '</div>';
}

function renderCharityMini(c) {
  const el = document.getElementById("charityMini");
  if (!c) { el.innerHTML = '<div class="empty-state">No active request yet.</div>'; return; }
  el.innerHTML = '<div class="mini-card">'
    + '<div class="title">' + escapeHtml(c.charityName) + '</div>'
    + '<div class="sub">' + escapeHtml(c.deliveryLocation || "") + '</div>'
    + '<dl>'
    + miniField("Requested", c.requestedQuantity)
    + miniField("Accepts", (c.acceptedDietaryTypes || []).join(", ") || "-")
    + miniField("Rejects", (c.rejectedAllergens || []).join(", ") || "None")
    + miniField("Latest delivery", c.latestDeliveryTime || "-")
    + miniField("Can collect", c.canCollect ? "Yes" : "No")
    + miniField("Status", c.status)
    + '</dl></div>';
}

function renderDriverMini(d) {
  const el = document.getElementById("driverMini");
  if (!d) { el.innerHTML = '<div class="empty-state">No active driver yet.</div>'; return; }
  el.innerHTML = '<div class="mini-card">'
    + '<div class="title">' + escapeHtml(d.driverName) + '</div>'
    + '<div class="sub">' + escapeHtml(d.startingLocation || "") + '</div>'
    + '<dl>'
    + miniField("Available", (d.availableFrom || "-") + " – " + (d.availableUntil || "-"))
    + miniField("Capacity", d.capacityMeals + " meals")
    + miniField("Max distance", d.maximumDistanceMiles + " mi")
    + miniField("Insulated", d.hasInsulatedStorage ? "Yes" : "No")
    + miniField("Status", d.status)
    + '</dl></div>';
}

async function refreshEntities() {
  try {
    const [donations, charities, drivers] = await Promise.all([
      api("/api/donations"), api("/api/charity-requests"), api("/api/drivers")
    ]);
    renderDonationMini(donations[0]);
    renderCharityMini(charities[0]);
    renderDriverMini(drivers[0]);
  } catch (e) { console.error(e); }
}

async function refreshPlanAndSafety() {
  try {
    const plan = await api("/api/rescue-plan");
    const safetyEl = document.getElementById("safetyChecks");
    if (!plan) {
      safetyEl.innerHTML = '<div class="empty-state">Run the negotiation to see safety checks.</div>';
      renderPlanCard(null, document.getElementById("planContainer"));
      return;
    }
    safetyEl.innerHTML = (plan.safetyChecks || []).map(renderCheckItem).join("") || '<div class="empty-state">No checks recorded.</div>';
    renderPlanCard(plan, document.getElementById("planContainer"), {
      confirmButton: true,
      onConfirm: async (planId) => {
        setStatus("Confirming rescue…");
        await api("/api/rescue-plan/" + planId + "/confirm", { method: "POST" });
        setStatus("Rescue confirmed.");
        await refreshEntities();
        await refreshPlanAndSafety();
        await refreshImpact();
      }
    });
  } catch (e) { console.error(e); }
}

async function refreshImpact() {
  try {
    const stats = await api("/api/impact");
    if (stats.mealsRescued !== lastMealsRescued) {
      animateCountUp(document.getElementById("statMeals"), stats.mealsRescued);
      animateCountUp(document.getElementById("statWaste"), stats.foodWastePreventedKg, 1);
      animateCountUp(document.getElementById("statPeople"), stats.peopleSupported);
      animateCountUp(document.getElementById("statDistance"), stats.distanceMilesTravelled, 1);
      lastMealsRescued = stats.mealsRescued;
    }
  } catch (e) { console.error(e); }
}

function restartFeed() {
  if (feedTimer) clearInterval(feedTimer);
  feedTimer = startAgentFeedPolling(document.getElementById("feed"), () => {
    refreshPlanAndSafety();
    refreshImpact();
  });
}

document.getElementById("loadSupermarketBtn").addEventListener("click", async () => {
  await api("/api/demo/load?role=supermarket", { method: "POST" });
  await refreshEntities();
  setStatus("Loaded demo supermarket donation.");
});
document.getElementById("loadCharityBtn").addEventListener("click", async () => {
  await api("/api/demo/load?role=charity", { method: "POST" });
  await refreshEntities();
  setStatus("Loaded demo charity request.");
});
document.getElementById("loadDriverBtn").addEventListener("click", async () => {
  await api("/api/demo/load?role=driver", { method: "POST" });
  await refreshEntities();
  setStatus("Loaded demo driver availability.");
});

document.getElementById("runNegotiationBtn").addEventListener("click", async () => {
  setStatus("Running agent negotiation…");
  restartFeed();
  try {
    const result = await api("/api/matching/run", { method: "POST" });
    if (!result.hadCandidate) {
      setStatus("Add at least one donation and one charity request (via the portals or demo loaders) before running the negotiation.");
      return;
    }
    setStatus("Negotiation complete - see the agent feed and final plan below.");
  } catch (e) {
    setStatus("Negotiation failed: " + e.message);
  }
});

document.getElementById("unsafeBtn").addEventListener("click", async () => {
  setStatus("Loading an unsafe scenario and running the negotiation…");
  restartFeed();
  try {
    const result = await api("/api/demo/unsafe", { method: "POST" });
    await refreshEntities();
    setStatus(result.hadCandidate ? "Unsafe match rejected - see the Safety Agent's reasoning below." : "Could not build the unsafe scenario.");
  } catch (e) {
    setStatus("Failed: " + e.message);
  }
});

document.getElementById("resetBtn").addEventListener("click", async () => {
  await api("/api/demo/reset", { method: "POST" });
  if (feedTimer) clearInterval(feedTimer);
  document.getElementById("feed").innerHTML = '<div class="feed-empty">Waiting for negotiation to start&hellip;</div>';
  await refreshEntities();
  await refreshPlanAndSafety();
  lastMealsRescued = -1;
  document.getElementById("statMeals").textContent = "0";
  document.getElementById("statWaste").textContent = "0";
  document.getElementById("statPeople").textContent = "0";
  document.getElementById("statDistance").textContent = "0";
  setStatus("Demo reset.");
});

// Initial load + steady background refresh so all open windows stay in sync.
refreshEntities();
refreshPlanAndSafety();
refreshImpact();
restartFeed();
setInterval(refreshEntities, 3000);
setInterval(refreshPlanAndSafety, 3000);
setInterval(refreshImpact, 3000);
