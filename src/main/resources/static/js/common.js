// Shared helpers used by every FoodFlow portal + the coordinator dashboard.

async function api(path, options) {
  const res = await fetch(path, Object.assign({
    headers: { "Content-Type": "application/json" }
  }, options));
  if (!res.ok && res.status !== 204) {
    const text = await res.text().catch(() => "");
    throw new Error("Request to " + path + " failed (" + res.status + "): " + text);
  }
  if (res.status === 204) return null;
  const contentType = res.headers.get("content-type") || "";
  return contentType.includes("application/json") ? res.json() : res.text();
}

function escapeHtml(s) {
  if (s === null || s === undefined) return "";
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function agentLabel(type) {
  const labels = {
    SUPERMARKET: "Supermarket Agent", CHARITY: "Charity Agent", DRIVER: "Driver Agent",
    SAFETY: "Safety Agent", ROUTING: "Routing Agent", COORDINATOR: "Coordinator Agent", GEMINI: "Gemini"
  };
  return labels[type] || type;
}

function agentIcon(type) {
  const icons = {
    SUPERMARKET: "🏪", CHARITY: "❤️", DRIVER: "🚗",
    SAFETY: "🛡️", ROUTING: "📍", COORDINATOR: "🧭", GEMINI: "✨"
  };
  return icons[type] || "•";
}

function renderFeedMessage(msg) {
  return '<div class="feed-msg sev-' + msg.severity + ' agent-' + msg.agentType + '">'
    + '<div class="who">' + agentIcon(msg.agentType) + ' ' + escapeHtml(agentLabel(msg.agentType)) + '</div>'
    + '<div class="body">' + escapeHtml(msg.message) + '</div>'
    + '</div>';
}

function renderCheckItem(check) {
  return '<div class="check-item ' + (check.passed ? "pass" : "fail") + '">'
    + '<div class="icon">' + (check.passed ? "✓" : "✕") + '</div>'
    + '<div><span class="name">' + escapeHtml(check.name) + ':</span> '
    + '<span class="detail">' + escapeHtml(check.detail) + '</span></div>'
    + '</div>';
}

function severityBadgeClass(sev) {
  return { INFO: "badge-info", SUCCESS: "badge-success", WARNING: "badge-warning", ERROR: "badge-error" }[sev] || "badge-neutral";
}

function fmtMiles(m) {
  if (m === null || m === undefined) return "-";
  return (Math.round(m * 10) / 10) + " mi";
}

// Renders a rescue-plan result (proposed/confirmed/rejected) into a container element.
function renderPlanCard(plan, containerEl, opts) {
  opts = opts || {};
  if (!plan) {
    containerEl.innerHTML = '<div class="empty-state">No rescue plan yet.</div>';
    return;
  }
  const rejected = !plan.safetyPassed;
  const checksHtml = (plan.safetyChecks || []).map(renderCheckItem).join("");

  let planFields = '';
  if (!rejected) {
    planFields = '<div class="plan-grid">'
      + planField("Donation", plan.allocatedMeals + " " + (plan.dietaryTypes || []).join(", ") + " meals")
      + planField("Supermarket", plan.supermarketName)
      + planField("Charity", plan.charityName)
      + planField("Volunteer driver", plan.driverName || "Self-collected by charity")
      + planField("Pickup", plan.pickupTime || "-")
      + planField("Estimated delivery", plan.estimatedDeliveryTime || "-")
      + planField("Estimated distance", fmtMiles(plan.estimatedDistanceMiles))
      + planField("Food deadline", plan.foodDeadline || "-")
      + '</div>';
  }

  let scoreHtml = '';
  if (!rejected && plan.scoreBreakdown) {
    const b = plan.scoreBreakdown;
    scoreHtml = '<div class="score-header"><span>Match score</span><span class="pct">' + plan.matchScorePercent + '%</span></div>'
      + scoreRow("Dietary compatibility", b.dietaryCompatibility, b.dietaryCompatibilityMax)
      + scoreRow("Allergen compatibility", b.allergenCompatibility, b.allergenCompatibilityMax)
      + scoreRow("Collection timing", b.collectionTiming, b.collectionTimingMax)
      + scoreRow("Quantity fulfilled", b.quantityFulfilled, b.quantityFulfilledMax)
      + scoreRow("Driver capacity", b.driverCapacity, b.driverCapacityMax)
      + scoreRow("Travel distance", b.travelDistance, b.travelDistanceMax);
  }

  const title = rejected ? "MATCH REJECTED" : (plan.status === "CONFIRMED" ? "FOODFLOW RESCUE CONFIRMED" : "PROPOSED RESCUE PLAN");

  containerEl.innerHTML = '<div class="plan-card ' + (rejected ? "rejected" : "") + '">'
    + '<div class="plan-title">' + title + '</div>'
    + planFields
    + (rejected ? '<div class="check-list" style="margin-bottom:14px;">' + checksHtml + '</div>' : '')
    + (scoreHtml ? '<div style="margin-bottom:16px;">' + scoreHtml + '</div>' : '')
    + (plan.explanation ? '<div class="plan-explanation"><span class="tag">✨ Gemini explanation</span>' + escapeHtml(plan.explanation) + '</div>' : '')
    + (opts.showChecks && !rejected ? '<div class="check-list" style="margin-top:14px;">' + checksHtml + '</div>' : '')
    + (opts.confirmButton && !rejected && plan.status === "PROPOSED" ? '<div class="btn-row"><button class="btn-primary" id="confirmRescueBtn">Confirm rescue</button></div>' : '')
    + '</div>';

  if (opts.confirmButton && !rejected && plan.status === "PROPOSED") {
    document.getElementById("confirmRescueBtn").addEventListener("click", () => opts.onConfirm && opts.onConfirm(plan.id));
  }
}

function planField(k, v) {
  return '<div class="plan-field"><div class="k">' + escapeHtml(k) + '</div><div class="v">' + escapeHtml(v) + '</div></div>';
}

function scoreRow(label, amt, max) {
  const pct = max > 0 ? Math.round((amt / max) * 100) : 0;
  return '<div class="score-row"><div class="label">' + escapeHtml(label) + '</div>'
    + '<div class="bar-track"><div class="bar-fill" style="width:' + pct + '%"></div></div>'
    + '<div class="amt">' + amt + '/' + max + '</div></div>';
}

function animateCountUp(el, target, decimals) {
  decimals = decimals || 0;
  const start = 0;
  const duration = 900;
  const startTime = performance.now();
  function tick(now) {
    const progress = Math.min(1, (now - startTime) / duration);
    const eased = 1 - Math.pow(1 - progress, 3);
    const value = start + (target - start) * eased;
    el.textContent = decimals > 0 ? value.toFixed(decimals) : Math.round(value);
    if (progress < 1) requestAnimationFrame(tick);
    else el.textContent = decimals > 0 ? target.toFixed(decimals) : target;
  }
  requestAnimationFrame(tick);
}

// Polls GET /api/agent-messages and renders newly-revealed messages into feedEl.
function startAgentFeedPolling(feedEl, onComplete) {
  let renderedCount = 0;
  feedEl.innerHTML = '<div class="feed-empty">Waiting for negotiation to start&hellip;</div>';
  const timer = setInterval(async () => {
    try {
      const data = await api("/api/agent-messages");
      const messages = data.messages || [];
      if (messages.length > renderedCount) {
        if (renderedCount === 0) feedEl.innerHTML = "";
        for (let i = renderedCount; i < messages.length; i++) {
          feedEl.insertAdjacentHTML("beforeend", renderFeedMessage(messages[i]));
        }
        feedEl.scrollTop = feedEl.scrollHeight;
        renderedCount = messages.length;
      }
      if (data.complete && messages.length > 0) {
        clearInterval(timer);
        if (onComplete) onComplete();
      }
    } catch (e) {
      console.error(e);
    }
  }, 500);
  return timer;
}
