// VaahanSetu Production Configuration
// To override the API URL for custom deployments, uncomment and set the URL below:
// window.VAAHAN_API_URL = "https://vaahansetu-api.onrender.com/api";

window.VAAHAN_API_URL = window.VAAHAN_API_URL || (
    (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
        ? "http://localhost:8080/api"
        : `${window.location.origin}/api`
);
