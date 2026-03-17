console.log("index.js loaded");
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

let pendingTempToken = null;

function login() {
    console.log("Login button clicked");
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const resultEl = document.getElementById("result");
    if (resultEl) resultEl.innerText = "";

    auth.signInWithEmailAndPassword(email, password)
        .then(userCredential => userCredential.user.getIdToken())
        .then(idToken => {
            return fetch('/api/sessionLogin', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ idToken })
            });
        })
        .then(res => {
            if (!res.ok) return Promise.reject(new Error("Login failed"));
            const ct = res.headers.get("Content-Type") || "";
            return ct.includes("application/json") ? res.json() : Promise.resolve({});
        })
        .then(data => {
            if (data && data.totpRequired && data.tempToken) {
                pendingTempToken = data.tempToken;
                document.getElementById("loginForm").style.display = "none";
                document.getElementById("totpStep").style.display = "block";
                document.getElementById("totpCode").value = "";
                document.getElementById("totpCode").focus();
                return;
            }
            window.location.href = "/dashboard";
        })
        .catch(error => {
            if (resultEl) resultEl.innerText = "Login failed. Invalid email or password.";
            console.log("Login error:", error);
        });
}

function verifyTotp() {
    const code = document.getElementById("totpCode").value.trim();
    const resultEl = document.getElementById("totpResult") || document.getElementById("result");
    if (resultEl) resultEl.innerText = "";
    if (!pendingTempToken || code.length !== 6) {
        if (resultEl) resultEl.innerText = "Please enter the 6-digit code.";
        return;
    }
    fetch('/api/verifyTotp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tempToken: pendingTempToken, code: code })
    })
        .then(res => {
            if (res.ok) {
                window.location.href = "/dashboard";
                return;
            }

            if (res.status === 401) {
                throw new Error("Unauthorized");
            }

            throw new Error("Invalid code");
        })
        .catch((err) => {
            if (!resultEl) return;

            if (err && err.message === "Unauthorized") {
                resultEl.innerText = "Your code is invalid or expired . Please try again, or log in again.";
                return;
            }

            resultEl.innerText = "Invalid code. Try again.";
        });
}

auth.onIdTokenChanged(function(user){
    console.log("Auth state changed:", user);
});

document.addEventListener("DOMContentLoaded", function () {
    const loginForm = document.getElementById("loginForm");
    const verifyTotpBtn = document.getElementById("verifyTotpBtn");
    const totpCode = document.getElementById("totpCode");

    if (loginForm) {
        loginForm.addEventListener("submit", function (event) {
            event.preventDefault();
            login();
        });
    }
    if (verifyTotpBtn) {
        verifyTotpBtn.addEventListener("click", verifyTotp);
    }
    if (totpCode) {
        totpCode.addEventListener("keydown", function (e) {
            if (e.key === "Enter") verifyTotp();
        });
    }
});
