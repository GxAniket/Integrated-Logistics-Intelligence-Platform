import React, { useState } from "react";
import "./Login.css";

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

async function handleSubmit(e) {
  e.preventDefault();

  try {
    const response = await fetch("http://localhost:8080/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username: email,
        password: password,
      }),
    });

    const data = await response.json();

    if (response.ok) {
      localStorage.setItem("token", data.token);
      alert("Login successful!");
      console.log("Login response:", data);
    } else {
      alert(data.error || "Login failed");
    }
  } catch (error) {
    console.error("Error:", error);
    alert("Unable to connect to backend");
  }
}

  return (
    <div className="login-container">
      <div className="login-card">

        <div className="login-header">
          <h2>Login</h2>
        </div>

        <form onSubmit={handleSubmit} className="login-form">

          <div className="form-group">
            <label htmlFor="email">Email</label>

            <input
              id="email"
              type="email"
              name="email"
              placeholder="Email"
              value={email}
              required
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>

            <input
              id="password"
              type="password"
              name="password"
              placeholder="Password"
              value={password}
              required
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <div className="form-actions">
            <button type="submit" className="btn-primary">
              Login
            </button>
          </div>

          <p className="login-footer">
            Not a member?{" "}
            <a href="#" className="register-link">
              Register here
            </a>
          </p>

        </form>
      </div>
    </div>
  );
}

export default Login;