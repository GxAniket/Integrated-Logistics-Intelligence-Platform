import {
  ArrowRight,
  Box,
  MapPin,
  PackageCheck,
  Route,
} from "lucide-react";
import { Link } from "react-router-dom";
import "./Home.css";

function Home() {
  return (
    <div className="home-page">
      <header className="home-navbar">
        <Link to="/" className="home-logo">
          <div className="home-logo-icon">
            <Box size={21} />
          </div>
          <div className="home-logo-text">
            <strong>
              ShipTrack <span>Pro</span>
            </strong>
            <small>LOGISTICS MANAGEMENT</small>
          </div>
        </Link>

        <div className="home-nav-actions">
          <Link to="/login" className="nav-login">
            Sign in
          </Link>
          <Link to="/register" className="nav-start">
            Create account
            <ArrowRight size={16} />
          </Link>
        </div>
      </header>

      <main className="hero">
        <div className="hero-bg" aria-hidden="true"></div>
        <div className="hero-overlay" aria-hidden="true"></div>

        <section className="hero-content">
          <div className="hero-badge">
            <span></span>
            SHIPMENT MANAGEMENT
          </div>

          <h1>
            Create and manage
            <br />
            <span>your shipments.</span>
          </h1>

          <p className="hero-description">
            For the people moving goods and the people waiting for them.
            ShipTrack Pro helps businesses create shipments while customers,
            operators, and support teams stay in sync. Follow each delivery
            from pickup to doorstep with clear tracking and updates, all in one
            place.
          </p>

          <div className="hero-actions">
            <Link to="/register?role=business" className="primary-button">
              Create a shipment
              <ArrowRight size={18} />
            </Link>
            <Link to="/login" className="secondary-button">
              <PackageCheck size={17} />
              Sign in
            </Link>
          </div>
        </section>

        <aside className="journey-card" aria-label="How ShipTrack Pro connects a shipment journey">
          <div className="journey-card-heading">
            <div className="journey-card-icon">
              <Route size={18} />
            </div>
            <div>
              <span>ONE SHARED JOURNEY</span>
              <h2>Every handoff, connected.</h2>
            </div>
          </div>

          <p className="journey-card-intro">
            From the first shipment details to the final delivery, everyone has
            a part to play.
          </p>

          <ol className="journey-steps">
            <li>
              <span className="journey-step-icon journey-step-business">
                <Box size={16} />
              </span>
              <span>
                <strong>Set the journey in motion</strong>
                <small>Businesses create shipment details</small>
              </span>
            </li>
            <li>
              <span className="journey-step-icon journey-step-operations">
                <Route size={16} />
              </span>
              <span>
                <strong>Keep every handoff moving</strong>
                <small>Operators coordinate the delivery</small>
              </span>
            </li>
            <li>
              <span className="journey-step-icon journey-step-customer">
                <MapPin size={16} />
              </span>
              <span>
                <strong>Stay close to the progress</strong>
                <small>Customers follow their shipment</small>
              </span>
            </li>
          </ol>
          <div className="journey-card-footer">
            <PackageCheck size={15} />
            <span>One clear view, from pickup to doorstep.</span>
          </div>
        </aside>
      </main>
    </div>
  );
}

export default Home;
