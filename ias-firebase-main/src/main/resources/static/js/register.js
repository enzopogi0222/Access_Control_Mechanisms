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

const auth = firebase.auth();

function register() {
  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value;
  const confirmPassword = document.getElementById("confirmPassword").value;
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

  auth.createUserWithEmailAndPassword(email, password)
    .then((userCredential) => {
      if (result) {
        result.style.color = "green";
        result.innerText = "User registered successfully! Redirecting to dashboard...";
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
