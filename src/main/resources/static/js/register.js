const firebaseConfig = {
  apiKey: "AIzaSyDFQpn2ADkTg0F5t4uo4cwkyRDg51fkUn4",
  authDomain: "iasgroup101.firebaseapp.com",
  projectId: "iasgroup101",
  storageBucket: "iasgroup101.firebasestorage.app",
  messagingSenderId: "408027022887",
  appId: "1:408027022887:web:06e094c39e16daf2ac3002",
  measurementId: "G-FFYLQBCLM2"
};

if (!firebase.apps.length) {
  firebase.initializeApp(firebaseConfig);
}

// Use the default app for the *current* signed-in user.
const auth = firebase.auth();
// Use a secondary app to create new users without switching the current session.
const secondaryApp = firebase.apps.find(a => a && a.name === "secondary")
  ? firebase.app("secondary")
  : firebase.initializeApp(firebaseConfig, "secondary");
const secondaryAuth = secondaryApp.auth();

function register() {
  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value;
  const confirmPassword = document.getElementById("confirmPassword").value;
  const roleEl = document.getElementById("role");
  const role = roleEl ? roleEl.value : "user";
  const result = document.getElementById("result");
  const registerBtn = document.getElementById("registerBtn");

  if (result) result.innerText = "";

  if (!email || !password || !confirmPassword) {
    if (result) result.innerText = "All fields are required.";
    return;
  }

  if (password !== confirmPassword) {
    if (result) result.innerText = "Passwords do not match.";
    return;
  }

  if (password.length < 6) {
    if (result) result.innerText = "Password must be at least 6 characters.";
    return;
  }

  if (registerBtn) registerBtn.disabled = true;

  let createdUid = null;

  secondaryAuth.createUserWithEmailAndPassword(email, password)
    .then((userCredential) => {
      createdUid = userCredential && userCredential.user ? userCredential.user.uid : null;
      return secondaryAuth.signOut();
    })
    .then(() => {
      if (!createdUid) {
        throw new Error("Registration succeeded but UID was not returned.");
      }
      return fetch("/api/admin/roles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({ uid: createdUid, role: role })
      });
    })
    .then(async (res) => {
      if (!res.ok) {
        let errText = "Failed to assign role.";
        try {
          const data = await res.json();
          if (data && data.error) errText = data.error;
        } catch (_) {}
        throw new Error(errText);
      }
      return res.json().catch(() => ({}));
    })
    .then(() => {
      if (result) {
        result.style.color = "green";
        result.innerText = `User registered successfully as ${role}! Returning to dashboard...`;
      }
      setTimeout(() => {
        window.location.href = "/dashboard";
      }, 2000);
    })
    .catch((error) => {
      if (registerBtn) registerBtn.disabled = false;
      if (result) {
        result.style.color = "red";
        let message = "Registration error: " + error.message;
        if (error.code === "auth/email-already-in-use") {
          message = "That email is already in use.";
        }
        result.innerText = message;
      }
      console.error(error);
    });
}

document.addEventListener("DOMContentLoaded", function () {
  const registerForm = document.getElementById("registerForm");
  if (registerForm) {
    registerForm.addEventListener("submit", function (event) {
      event.preventDefault(); // Prevent the ? reloading
      register();
    });
  }
});
