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

  result.innerText = "";

  if (!email || !password || !confirmPassword) {
    result.innerText = "All fields are required.";
    return;
  }

  if (password !== confirmPassword) {
    result.innerText = "Passwords do not match.";
    return;
  }

  // Basic password strength check can be enhanced
  if (password.length < 6) {
    result.innerText = "Password must be at least 6 characters.";
    return;
  }

  registerBtn.disabled = true;

  auth.createUserWithEmailAndPassword(email, password)
    .then((userCredential) => {
      // Success
      result.style.color = "green";
      result.innerText = "Registration successful! Redirecting to login...";

      setTimeout(() => {
        window.location.href = "/index";
      }, 2000);
    })
    .catch((error) => {
      registerBtn.disabled = false;
      result.style.color = "red";
      let message = "Registration error: " + error.message;

      // Map common error codes if needed, or just use error.message
      if (error.code === 'auth/email-already-in-use') {
        message = "That email is already in use.";
      }

      result.innerText = message;
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
