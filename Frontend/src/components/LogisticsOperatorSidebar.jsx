import { NavLink } from "react-router-dom";
import "./LogisticsOperatorSidebar.css";

const NAV_ITEMS = [
  ["⌂", "Dashboard", "/dashboard/operator"],
  ["▣", "Shipment Tracking", "/operator/shipment-tracking"],
  ["◎", "Live Deliveries", "/operator/live-delivery"],
  ["♙", "Driver Tracking", "/operator/driver-tracking"],
  ["⌁", "Route Management", "/operator/routes"],
  ["◷", "ETA & Delays", "/operator/eta-delay"],
  ["✓", "Proof of Delivery", "/operator/pod"],
];

function LogisticsOperatorSidebar() {
  return (
    <aside className="logistics-operator-sidebar">
      <div className="logistics-operator-brand">
        <div className="logistics-operator-brand-logo">S</div>
        <div>
          <h2>ShipTrack</h2>
          <span>Operator Console</span>
        </div>
      </div>

      <nav className="logistics-operator-nav">
        {NAV_ITEMS.map(([icon, label, path]) => (
          <NavLink
            key={path}
            to={path}
            end
            className={({ isActive }) =>
              `logistics-operator-nav-link${isActive ? " active" : ""}`
            }
          >
            <span className="logistics-operator-nav-icon">{icon}</span>
            <span className="logistics-operator-nav-label">{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="logistics-operator-sidebar-bottom">
        <div className="logistics-operator-user">
          <div className="logistics-operator-avatar">OP</div>
          <div>
            <strong>Logistics Operator</strong>
            <span>Operations Team</span>
          </div>
        </div>

        <NavLink
          to="/login"
          className="logistics-operator-logout"
          onClick={() => {
            localStorage.removeItem("shiptrackToken");
            localStorage.removeItem("shiptrackUser");
          }}
        >
          <span>↪</span>
          Logout
        </NavLink>
      </div>
    </aside>
  );
}

export default LogisticsOperatorSidebar;
