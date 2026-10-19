import { NavLink } from "react-router-dom";
import "./BusinessClientSidebar.css";

const NAV_ITEMS = [
  ["⌂", "Overview", "/dashboard/business"],
  ["＋", "Create Shipment", "/business/create-shipment"],
  ["▣", "Shipment Management", "/business/shipment-management"],
  ["◷", "Shipment History", "/business/shipment-history"],
  ["□", "Package Information", "/business/package-information"],
  ["⌖", "Tracking", "/business/tracking"],
  ["↗", "Delivery Performance", "/business/delivery-performance"],
  ["!", "Delay Analysis", "/business/delay-analysis"],
  ["◎", "Logistics Overview", "/business/logistics-overview"],
  ["♙", "Customer Activity", "/business/customer-activity"],
  ["▥", "Reports & Export", "/business/reports"],
  ["♢", "Notifications", "/business/notifications"],
];

function BusinessClientSidebar() {
  return (
    <aside className="business-client-sidebar">
      <div className="business-client-logo">
        <div className="business-client-logo-icon">S</div>
        <div>
          <h2>ShipTrack Pro</h2>
          <span>LOGISTICS INTELLIGENCE</span>
        </div>
      </div>

      <div className="business-client-menu-title">MAIN MENU</div>

      <nav className="business-client-navigation">
        {NAV_ITEMS.map(([icon, label, path]) => (
          <NavLink
            key={path}
            to={path}
            end
            className={({ isActive }) =>
              `business-client-nav-link${isActive ? " active" : ""}`
            }
          >
            <span>{icon}</span>
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="business-client-sidebar-bottom">
        <NavLink
          to="/login"
          className="business-client-logout"
          onClick={() => {
            localStorage.removeItem("shiptrackToken");
            localStorage.removeItem("shiptrackUser");
          }}
        >
          ⇥ Logout
        </NavLink>
      </div>
    </aside>
  );
}

export default BusinessClientSidebar;
