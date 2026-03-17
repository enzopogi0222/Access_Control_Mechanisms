// MAC page wiring: calls your Spring endpoints (/api/mac, /api/files, /api/admin/*)

const firebaseConfig = {
    apiKey: "AIzaSyDFQpn2ADkTg0F5t4uo4cwkyRDg51fkUn4",
    authDomain: "iasgroup101.firebaseapp.com",
    projectId: "iasgroup101",
    storageBucket: "iasgroup101.firebasestorage.app",
    messagingSenderId: "408027022887",
    appId: "1:408027022887:web:06e094c39e16daf2ac3002",
    measurementId: "G-FFYLQBCLM2"
};

// Avoid "Firebase App named '[DEFAULT]' already exists".
try {
    if (!firebase.apps || firebase.apps.length === 0) firebase.initializeApp(firebaseConfig);
} catch (e) {
    // ignore
}

const auth = firebase.auth();

const userEmailEl = document.getElementById("userEmail");
const btnLogout = document.getElementById("btnLogout");

function api(path, options) {
    return fetch(path, { credentials: "include", ...options });
}

function setText(id, text, isError) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    if (el.classList.contains("dac-message")) {
        el.style.color = isError ? "#c62828" : "#2e7d32";
    }
}

function prettyJson(v) {
    try { return JSON.stringify(v, null, 2); } catch { return String(v); }
}

function selectedLevel(id) {
    const el = document.getElementById(id);
    return el && el.value ? el.value : "PUBLIC";
}

// --- auth/session ---
auth.onAuthStateChanged(function(user) {
    if (!user) {
        window.location.href = "/index";
        return;
    }
    if (userEmailEl) userEmailEl.textContent = user.email || "—";
    refreshMe();
});

if (btnLogout) {
    btnLogout.addEventListener("click", function() {
        auth.signOut().then(() => {
            fetch("/api/sessionLogout", { method: "POST" })
                .finally(() => (window.location.href = "/index"));
        });
    });
}

// --- me/clearance ---
function refreshMe() {
    setText("macMe", "Loading…");
    api("/api/mac/me")
        .then(async (res) => {
            const isJson = (res.headers.get("content-type") || "").includes("json");
            const payload = isJson ? await res.json() : await res.text();
            return { ok: res.ok, status: res.status, payload };
        })
        .then(({ ok, status, payload }) => {
            if (!ok) {
                setText("macMe", "Failed (" + status + "): " + prettyJson(payload), true);
                return;
            }
            setText("macMe", "UID: " + payload.uid + " | Clearance: " + payload.clearance);
        })
        .catch(() => setText("macMe", "Request failed", true));
}

const btnRefreshMe = document.getElementById("btnRefreshMe");
if (btnRefreshMe) btnRefreshMe.addEventListener("click", refreshMe);

// --- upload with label ---
const btnMacUpload = document.getElementById("btnMacUpload");
if (btnMacUpload) {
    btnMacUpload.addEventListener("click", function() {
        const fileInput = document.getElementById("macFileUpload");
        const file = fileInput && fileInput.files ? fileInput.files[0] : null;
        if (!file) {
            setText("macUploadMessage", "Choose a file first.", true);
            return;
        }

        const level = selectedLevel("macClassification");
        const form = new FormData();
        form.append("file", file, file.name);

        setText("macUploadMessage", "Uploading as " + level + "…");
        api("/api/files/upload?classification=" + encodeURIComponent(level), { method: "POST", body: form })
            .then(async (res) => {
                const isJson = (res.headers.get("content-type") || "").includes("json");
                const payload = isJson ? await res.json() : await res.text();
                return { ok: res.ok, status: res.status, payload };
            })
            .then(({ ok, status, payload }) => {
                if (!ok) {
                    setText("macUploadMessage", "Denied (" + status + "): " + prettyJson(payload), true);
                    return;
                }
                setText("macUploadMessage", "Uploaded id=" + payload.id + " classification=" + payload.classification);
                if (fileInput) fileInput.value = "";
                const idEl = document.getElementById("macFileId");
                if (idEl) idEl.value = payload.id;
                updateDownloadLink();
            })
            .catch(() => setText("macUploadMessage", "Request failed", true));
    });
}

// --- read metadata & download ---
const macFileId = document.getElementById("macFileId");
const macDownloadLink = document.getElementById("macDownloadLink");

function updateDownloadLink() {
    if (!macDownloadLink) return;
    const id = macFileId && macFileId.value ? macFileId.value.trim() : "";
    if (!id) {
        macDownloadLink.href = "#";
        macDownloadLink.style.pointerEvents = "none";
        macDownloadLink.style.opacity = "0.6";
        return;
    }
    macDownloadLink.href = "/api/files/" + encodeURIComponent(id) + "/download";
    macDownloadLink.style.pointerEvents = "auto";
    macDownloadLink.style.opacity = "1";
}

if (macFileId) macFileId.addEventListener("input", updateDownloadLink);
updateDownloadLink();

const btnMacRead = document.getElementById("btnMacRead");
if (btnMacRead) {
    btnMacRead.addEventListener("click", function() {
        const id = macFileId && macFileId.value ? macFileId.value.trim() : "";
        if (!id) {
            setText("macReadResult", "Paste a File ID first.", true);
            return;
        }
        setText("macReadResult", "Loading…");
        api("/api/files/" + encodeURIComponent(id))
            .then(async (res) => {
                const isJson = (res.headers.get("content-type") || "").includes("json");
                const payload = isJson ? await res.json() : await res.text();
                return { ok: res.ok, status: res.status, payload };
            })
            .then(({ ok, status, payload }) => {
                if (!ok) {
                    setText("macReadResult", "Denied (" + status + ")\n\n" + prettyJson(payload), true);
                    return;
                }
                setText("macReadResult", prettyJson(payload));
            })
            .catch(() => setText("macReadResult", "Request failed", true));
    });
}

// --- admin: set clearance ---
const btnSetClearance = document.getElementById("btnSetClearance");
if (btnSetClearance) {
    btnSetClearance.addEventListener("click", function() {
        const uidEl = document.getElementById("adminTargetUid");
        const uid = uidEl && uidEl.value ? uidEl.value.trim() : "";
        if (!uid) {
            setText("adminMessage", "Paste a target UID.", true);
            return;
        }
        const level = selectedLevel("adminClearance");
        setText("adminMessage", "Saving clearance…");
        api("/api/admin/clearance", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ uid: uid, clearance: level })
        })
            .then(async (res) => {
                const isJson = (res.headers.get("content-type") || "").includes("json");
                const payload = isJson ? await res.json() : await res.text();
                return { ok: res.ok, status: res.status, payload };
            })
            .then(({ ok, status, payload }) => {
                if (!ok) {
                    setText("adminMessage", "Denied (" + status + "): " + prettyJson(payload), true);
                    return;
                }
                setText("adminMessage", "Set clearance for " + uid + " to " + level);
            })
            .catch(() => setText("adminMessage", "Request failed", true));
    });
}

// --- admin: audit ---
const btnLoadAudit = document.getElementById("btnLoadAudit");
if (btnLoadAudit) {
    btnLoadAudit.addEventListener("click", function() {
        setText("adminMessage", "Loading audit…");
        api("/api/admin/audit?limit=50")
            .then(async (res) => {
                const isJson = (res.headers.get("content-type") || "").includes("json");
                const payload = isJson ? await res.json() : await res.text();
                return { ok: res.ok, status: res.status, payload };
            })
            .then(({ ok, status, payload }) => {
                if (!ok) {
                    setText("adminMessage", "Denied (" + status + "): " + prettyJson(payload), true);
                    return;
                }
                setText("adminMessage", "Loaded audit events.");
                setText("adminAudit", prettyJson(payload));
            })
            .catch(() => setText("adminMessage", "Request failed", true));
    });
}

