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
