const firebaseConfig = {
    apiKey: "AIzaSyDFQpn2ADkTg0F5t4uo4cwkyRDg51fkUn4",
    authDomain: "iasgroup101.firebaseapp.com",
    projectId: "iasgroup101",
    storageBucket: "iasgroup101.firebasestorage.app",
    messagingSenderId: "408027022887",
    appId: "1:408027022887:web:06e094c39e16daf2ac3002",
    measurementId: "G-FFYLQBCLM2"
};

firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();

const userEmailEl = document.getElementById("userEmail");
const btnLogout = document.getElementById("btnLogout");


auth.onAuthStateChanged(function(user) {
    if (user) {
        userEmailEl.textContent = user.email;
    } else {
        window.location.href = "/index";
    }
});


function loadSecureContent() {
    fetch("/api/secure")
        .then(res => {
            if (!res.ok) throw new Error("Unauthorized");
            return res.text();
        })
        .then(data => {
            console.log("Secure response:", data);
        })
        .catch(() => {
            window.location.href = "/index";
        });
}

loadSecureContent();

// Logout
btnLogout.addEventListener("click", function() {
    auth.signOut().then(() => {
        fetch('/api/sessionLogout', { method: 'POST' })
            .finally(() => {
                window.location.href = "/index";
            });
    });
});

// ——— DAC: File resources (test from dashboard) ———
const dacFileUpload = document.getElementById("dacFileUpload");
const btnUploadFile = document.getElementById("btnUploadFile");
const btnListFiles = document.getElementById("btnListFiles");
const btnListSharedFiles = document.getElementById("btnListSharedFiles");
const dacMessage = document.getElementById("dacMessage");
const dacFileList = document.getElementById("dacFileList");

function showDacMessage(text, isError) {
    if (!dacMessage) return;
    dacMessage.textContent = text;
    dacMessage.style.color = isError ? "#c62828" : "#2e7d32";
}

function api(path, options) {
    return fetch(path, { credentials: "include", ...options });
}

function escapeHtml(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function renderFileList(list) {
    if (!dacFileList) return;
    if (!Array.isArray(list) || list.length === 0) {
        dacFileList.innerHTML = "<div class=\"dac-empty\">No files yet.</div>";
        return;
    }

    const rows = list.map(function(f) {
        const id = escapeHtml(f.id);
        const filename = escapeHtml(f.filename || "file");
        const size = (typeof f.sizeBytes === "number") ? f.sizeBytes : null;
        const uploadedAt = escapeHtml(f.uploadedAt || "");
        const downloadHref = "/api/files/" + id + "/download";
        const allowedUsersCount = Array.isArray(f.allowedUsers) ? f.allowedUsers.length : 0;
        return (
            "<tr>" +
            "<td class=\"dac-cell-id\"><code class=\"dac-id\" title=\"" + id + "\">" + id + "</code> " +
            "<button type=\"button\" class=\"dac-copy\" data-copy=\"" + id + "\">Copy</button></td>" +
            "<td class=\"dac-cell-name\">" + filename + "</td>" +
            "<td class=\"dac-cell-meta\">" + (size != null ? (size + " bytes") : "—") + "</td>" +
            "<td class=\"dac-cell-meta\">" + (uploadedAt || "—") + "</td>" +
            "<td class=\"dac-cell-actions\"><a class=\"dac-download\" href=\"" + downloadHref + "\" target=\"_blank\" rel=\"noreferrer\">Download</a></td>" +
            "<td class=\"dac-cell-share\">" +
              "<div class=\"dac-share-wrap\">" +
                "<input class=\"dac-share-input\" type=\"text\" placeholder=\"User UID or email\" data-share-input=\"" + id + "\" />" +
                "<button type=\"button\" class=\"dac-share-btn\" data-share-id=\"" + id + "\">Share</button>" +
              "</div>" +
              "<div class=\"dac-share-hint\">Allowed: " + allowedUsersCount + "</div>" +
            "</td>" +
            "</tr>"
        );
    }).join("");

    dacFileList.innerHTML =
        "<table class=\"dac-table\">" +
        "<thead><tr><th>ID</th><th>Filename</th><th>Size</th><th>Uploaded</th><th></th><th>Share</th></tr></thead>" +
        "<tbody>" + rows + "</tbody>" +
        "</table>";

    // Wire copy buttons (re-render replaces DOM)
    dacFileList.querySelectorAll("button.dac-copy").forEach(function(btn) {
        btn.addEventListener("click", function() {
            const value = btn.getAttribute("data-copy") || "";
            if (!value) return;
            navigator.clipboard.writeText(value)
                .then(function() { showDacMessage("Copied file id to clipboard."); })
                .catch(function() { showDacMessage("Copy failed (browser blocked clipboard).", true); });
        });
    });

    // Wire share buttons (re-render replaces DOM)
    dacFileList.querySelectorAll("button.dac-share-btn").forEach(function(btn) {
        btn.addEventListener("click", function() {
            const fileId = btn.getAttribute("data-share-id") || "";
            if (!fileId) return;

            const input = dacFileList.querySelector("input.dac-share-input[data-share-input=\"" + CSS.escape(fileId) + "\"]");
            const allowedUser = input && input.value ? input.value.trim() : "";
            if (!allowedUser) {
                showDacMessage("Paste the target user's UID, then click Share.", true);
                return;
            }

            showDacMessage("Sharing…");
            api("/api/files/" + encodeURIComponent(fileId) + "/share", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ allowedUser: allowedUser })
            })
                .then(function(res) {
                    return (res.headers.get("content-type") || "").includes("json")
                        ? res.json().then(function(data) { return { ok: res.ok, data: data }; })
                        : res.text().then(function(t) { return { ok: res.ok, data: t ? { error: t } : { error: res.statusText } }; });
                })
                .then(function(result) {
                    if (result.ok) {
                        showDacMessage("Shared with UID: " + allowedUser);
                        if (input) input.value = "";
                        if (btnListFiles) btnListFiles.click();
                    } else {
                        showDacMessage(result.data && result.data.error ? result.data.error : "Share failed", true);
                    }
                })
                .catch(function() { showDacMessage("Request failed", true); });
        });
    });
}

if (btnUploadFile) {
    btnUploadFile.addEventListener("click", function() {
        const file = dacFileUpload && dacFileUpload.files ? dacFileUpload.files[0] : null;
        if (!file) {
            showDacMessage("Choose a file first.", true);
            return;
        }

        const form = new FormData();
        form.append("file", file, file.name);

        showDacMessage("Uploading…");
        api("/api/files/upload", { method: "POST", body: form })
            .then(function(res) {
                return (res.headers.get("content-type") || "").includes("json")
                    ? res.json().then(function(data) { return { ok: res.ok, data: data }; })
                    : res.text().then(function(t) { return { ok: res.ok, data: t ? { error: t } : { error: res.statusText } }; });
            })
            .then(function(result) {
                if (result.ok) {
                    showDacMessage("Uploaded: " + (result.data.filename || "file") + " (id: " + result.data.id + ")");
                    if (dacFileUpload) dacFileUpload.value = "";
                    // Refresh list after upload
                    if (btnListFiles) btnListFiles.click();
                } else {
                    showDacMessage(result.data && result.data.error ? result.data.error : "Upload failed", true);
                }
            })
            .catch(function() { showDacMessage("Request failed", true); });
    });
}

if (btnListFiles) {
    btnListFiles.addEventListener("click", function() {
        showDacMessage("Loading…");
        api("/api/files")
            .then(function(res) { return res.json(); })
            .then(function(list) {
                if (Array.isArray(list)) {
                    renderFileList(list);
                    showDacMessage("Listed " + list.length + " file(s).");
                    return;
                }
                if (dacFileList) dacFileList.textContent = JSON.stringify(list, null, 2);
                showDacMessage("Unexpected response", true);
            })
            .catch(function() {
                showDacMessage("Request failed", true);
                if (dacFileList) dacFileList.textContent = "";
            });
    });
}

if (btnListSharedFiles) {
    btnListSharedFiles.addEventListener("click", function() {
        showDacMessage("Loading…");
        api("/api/files/shared-with-me")
            .then(function(res) { return res.json(); })
            .then(function(list) {
                if (Array.isArray(list)) {
                    renderFileList(list);
                    showDacMessage("Listed " + list.length + " shared file(s).");
                    return;
                }
                if (dacFileList) dacFileList.textContent = JSON.stringify(list, null, 2);
                showDacMessage("Unexpected response", true);
            })
            .catch(function() {
                showDacMessage("Request failed", true);
                if (dacFileList) dacFileList.textContent = "";
            });
    });
}
