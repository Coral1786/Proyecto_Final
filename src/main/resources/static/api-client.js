async function apiRequest(url, options = {}) {
  const requestOptions = { ...options, credentials: "same-origin" };
  if (requestOptions.body && typeof requestOptions.body !== "string") {
    requestOptions.headers = { ...requestOptions.headers, "Content-Type": "application/json" };
    requestOptions.body = JSON.stringify(requestOptions.body);
  }

  const response = await fetch(url, requestOptions);
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "No se pudo completar la solicitud.");
  return data;
}

async function requireServerSession() {
  try {
    return await apiRequest("/api/auth/session");
  } catch {
    localStorage.removeItem("ingearqLoggedIn");
    localStorage.removeItem("ingearqRole");
    window.location.href = "index.html";
    return null;
  }
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[character]);
}

async function loadSelectOptions(select, url, selectedValue) {
  const options = await apiRequest(url);
  select.innerHTML = options
    .map((option) => `<option value="${escapeHtml(option.name)}">${escapeHtml(option.name)}</option>`)
    .join("");
  if (selectedValue !== undefined && selectedValue !== null) select.value = selectedValue;
}

document.addEventListener("DOMContentLoaded", () => {
  if (localStorage.getItem("ingearqRole") !== "admin") {
    document.querySelectorAll(".admin-only").forEach((element) => element.classList.add("hidden"));
  }
});
